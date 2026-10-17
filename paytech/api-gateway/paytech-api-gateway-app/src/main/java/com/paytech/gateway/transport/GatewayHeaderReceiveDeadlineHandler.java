package com.paytech.gateway.transport;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import reactor.netty.NettyPipeline;

/** 在 actual HTTP codec 前用同源官方 decoder 跟踪每个请求 Header 的接收时限。 */
public final class GatewayHeaderReceiveDeadlineHandler {
    private static final String PROBE_DECODER = GatewayHeaderReceiveDeadlineHandler.class.getName() + ".decoder";
    private static final String PROBE_SINK = GatewayHeaderReceiveDeadlineHandler.class.getName() + ".sink";
    private static final long HEADER_DEADLINE_SECONDS = 5;

    private GatewayHeaderReceiveDeadlineHandler() {}

    public static void install(ChannelPipeline pipeline, DecoderSettings decoderSettings) {
        Objects.requireNonNull(pipeline, "Gateway pipeline 不可为空");
        Objects.requireNonNull(decoderSettings, "Gateway HTTP decoder 配置不可为空");
        if (!(pipeline.get(NettyPipeline.HttpCodec) instanceof HttpServerCodec)) {
            throw new IllegalStateException("Gateway HTTP/1 codec pipeline 未就绪");
        }
        boolean decoderInstalled = pipeline.get(PROBE_DECODER) != null;
        boolean sinkInstalled = pipeline.get(PROBE_SINK) != null;
        if (decoderInstalled && sinkInstalled) {
            return;
        }
        if (decoderInstalled || sinkInstalled) {
            throw new IllegalStateException("Gateway Header framing probe pipeline 不完整");
        }
        HeaderReceiveState state = new HeaderReceiveState();
        ProbeMessageSink sink = new ProbeMessageSink(state);
        TrackingHttpRequestDecoder decoder =
            new TrackingHttpRequestDecoder(decoderSettings.toNettyConfig(), state, sink);
        sink.bind(decoder);
        pipeline.addBefore(NettyPipeline.HttpCodec, PROBE_DECODER, decoder);
        pipeline.addAfter(PROBE_DECODER, PROBE_SINK, sink);
    }

    /** Reactor actual decoder 的七个公开 HTTP/1 framing knob 的不可变快照。 */
    public record DecoderSettings(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
        boolean validateHeaders, int initialBufferSize, boolean allowDuplicateContentLengths,
        boolean allowPartialChunks) {

        private HttpDecoderConfig toNettyConfig() {
            return new HttpDecoderConfig().setMaxInitialLineLength(maxInitialLineLength).setMaxHeaderSize(maxHeaderSize)
                .setMaxChunkSize(maxChunkSize).setValidateHeaders(validateHeaders)
                .setInitialBufferSize(initialBufferSize).setAllowDuplicateContentLengths(allowDuplicateContentLengths)
                .setAllowPartialChunks(allowPartialChunks);
        }
    }

    private static final class TrackingHttpRequestDecoder extends HttpRequestDecoder {
        private final HeaderReceiveState state;
        private final ProbeMessageSink sink;

        private TrackingHttpRequestDecoder(HttpDecoderConfig decoderConfig, HeaderReceiveState state,
            ProbeMessageSink sink) {
            super(decoderConfig);
            this.state = state;
            this.sink = sink;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext context) throws Exception {
            state.bind(context);
            super.handlerAdded(context);
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (!(message instanceof ByteBuf buffer)) {
                context.fireChannelRead(message);
                return;
            }
            try {
                super.channelRead(context, buffer.retainedDuplicate());
            } catch (Exception exception) {
                failProbe(context, message);
                throw exception;
            } catch (Error error) {
                failProbe(context, message);
                throw error;
            }
            sink.forwardOriginal(buffer);
        }

        @Override
        protected void decode(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
            if (state.isTerminated()) {
                consume(input);
                return;
            }
            int existingOutput = output.size();
            if (input.isReadable()) {
                state.onDecoderInput();
            }
            super.decode(context, input, output);
            for (int index = existingOutput; index < output.size(); index++) {
                state.onDecoded(output.get(index));
            }
        }

        @Override
        protected void decodeLast(ChannelHandlerContext context, ByteBuf input, List<Object> output) throws Exception {
            if (state.isTerminated()) {
                consume(input);
                return;
            }
            super.decodeLast(context, input, output);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            terminateAndConsumePrivateCumulation();
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
            terminateAndConsumePrivateCumulation();
            super.exceptionCaught(context, cause);
        }

        @Override
        protected void handlerRemoved0(ChannelHandlerContext context) throws Exception {
            terminateAndConsumePrivateCumulation();
            try {
                super.handlerRemoved0(context);
            } finally {
                context.close();
            }
        }

        private void failProbe(ChannelHandlerContext context, Object originalMessage) {
            terminateAndConsumePrivateCumulation();
            ReferenceCountUtil.release(originalMessage);
            context.close();
        }

        private void terminateAndConsumePrivateCumulation() {
            state.terminate();
            consume(internalBuffer());
        }

        private void consume(ByteBuf input) {
            input.skipBytes(input.readableBytes());
        }
    }

    private static final class ProbeMessageSink extends ChannelInboundHandlerAdapter {
        private final HeaderReceiveState state;
        private ChannelHandlerContext forwardingContext;
        private TrackingHttpRequestDecoder decoder;

        private ProbeMessageSink(HeaderReceiveState state) {
            this.state = state;
        }

        private void bind(TrackingHttpRequestDecoder decoder) {
            this.decoder = Objects.requireNonNull(decoder, "Gateway Header framing decoder 不可为空");
        }

        @Override
        public void handlerAdded(ChannelHandlerContext context) throws Exception {
            forwardingContext = context;
            super.handlerAdded(context);
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (message instanceof HttpObject || message instanceof ByteBuf) {
                ReferenceCountUtil.release(message);
                return;
            }
            super.channelRead(context, message);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext context) throws Exception {
            forwardingContext = null;
            Objects.requireNonNull(decoder, "Gateway Header framing decoder 未绑定")
                .terminateAndConsumePrivateCumulation();
            context.close();
            super.handlerRemoved(context);
        }

        private void forwardOriginal(ByteBuf original) {
            ChannelHandlerContext context = forwardingContext;
            if (context == null) {
                state.terminate();
                ReferenceCountUtil.release(original);
                return;
            }
            context.fireChannelRead(original);
        }
    }

    private static final class HeaderReceiveState {
        private ChannelHandlerContext deadlineContext;
        private boolean receivingHeader;
        private boolean receivingBody;
        private boolean terminated;
        private ScheduledFuture<?> totalDeadline;
        private ScheduledFuture<?> idleDeadline;

        private void bind(ChannelHandlerContext context) {
            deadlineContext = context;
        }

        private void onDecoderInput() {
            if (terminated || receivingBody) {
                return;
            }
            if (!receivingHeader) {
                receivingHeader = true;
                totalDeadline = schedule();
            }
            cancel(idleDeadline);
            idleDeadline = schedule();
        }

        private boolean isTerminated() {
            return terminated;
        }

        private void onDecoded(Object message) {
            if (message instanceof HttpRequest request) {
                onRequestParsed(request);
            }
            if (message instanceof LastHttpContent) {
                receivingBody = false;
            }
        }

        private void onRequestParsed(HttpRequest request) {
            finishHeader();
            if (!request.decoderResult().isSuccess()) {
                receivingBody = false;
                return;
            }
            try {
                receivingBody =
                    HttpUtil.isTransferEncodingChunked(request) || HttpUtil.getContentLength(request, 0L) > 0;
            } catch (RuntimeException invalidContentLength) {
                receivingBody = false;
            }
        }

        private ScheduledFuture<?> schedule() {
            ChannelHandlerContext context =
                Objects.requireNonNull(deadlineContext, "Gateway Header deadline context 未绑定");
            return context.executor().schedule(() -> expire(context), HEADER_DEADLINE_SECONDS, TimeUnit.SECONDS);
        }

        private void expire(ChannelHandlerContext context) {
            if (terminated || !receivingHeader) {
                return;
            }
            finishHeader();
            context.close();
        }

        private void finishHeader() {
            receivingHeader = false;
            cancel(totalDeadline);
            cancel(idleDeadline);
            totalDeadline = null;
            idleDeadline = null;
        }

        private void terminate() {
            terminated = true;
            finishHeader();
            receivingBody = false;
            deadlineContext = null;
        }

        private void cancel(ScheduledFuture<?> future) {
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
