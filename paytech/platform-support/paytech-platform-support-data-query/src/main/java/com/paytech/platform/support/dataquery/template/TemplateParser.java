package com.paytech.platform.support.dataquery.template;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * 把词法单元组装成语法树：检查标签成对、未知标签、join 子节点限制与 part 位置，第一处错误即停止。
 */
final class TemplateParser {

    private static final String IF = "if";

    private static final String JOIN = "join";

    private static final String JOIN_CHILD_MESSAGE =
        "{{#join}} 内只能直接包含 {{#part}}、{{#if}}、{{#each}} 和空白，正文请放进 {{#part}}";

    private final Deque<OpenBlock> stack = new ArrayDeque<>();

    private final List<TemplateNode> root = new ArrayList<>();

    /** 尚未关闭的块标签。 */
    private static final class OpenBlock {

        /** 标签名：if、each、join、part。 */
        private final String name;

        /** 开始标签位置。 */
        private final SourcePosition position;

        /** 关闭时由当前块状态构造节点。 */
        private final Function<OpenBlock, TemplateNode> builder;

        /** 当前收集子节点的列表；if 块每进入一个分支换一个列表。 */
        private List<TemplateNode> children = new ArrayList<>();

        /** if 块已完成的分支。 */
        private final List<IfBranch> branches = new ArrayList<>();

        /** if 块当前分支的标签位置。 */
        private SourcePosition branchPosition;

        /** if 块当前分支的条件；#else 为 null。 */
        private TemplateExpression branchCondition;

        /** if 块是否已进入 #else。 */
        private boolean elseSeen;

        /**
         * 构造打开的块。
         *
         * @param name 块名
         * @param position 开始标签位置
         * @param builder 关闭时的节点构造
         */
        private OpenBlock(String name, SourcePosition position, Function<OpenBlock, TemplateNode> builder) {
            this.name = name;
            this.position = position;
            this.builder = builder;
        }
    }

    /**
     * 解析模板原文。
     *
     * @param body 模板原文
     * @return 语法树
     * @throws TemplateCheckException 第一处语法错误，带行列与原因
     */
    QueryTemplate parse(String body) {
        for (TemplateToken token : new TemplateScanner(body).scan()) {
            accept(token);
        }
        if (!stack.isEmpty()) {
            OpenBlock open = stack.peek();
            throw new TemplateCheckException(open.position, "{{#" + open.name + "}} 缺少结束标签 {{/" + open.name + "}}");
        }
        return new QueryTemplate(body, List.copyOf(root));
    }

    /**
     * 处理一个词法单元。
     *
     * @param token 词法单元
     */
    private void accept(TemplateToken token) {
        switch (token.getKind()) {
            case TEXT -> add(new TextNode(token.getPosition(), token.getContent()));
            case VALUE -> add(new ValueNode(token.getPosition(), valuePath(token)));
            case FRAGMENT -> add(new FragmentNode(token.getPosition(), fragmentName(token)));
            case TAG -> acceptTag(token);
        }
    }

    /**
     * 按标签名分派。
     *
     * @param token 标签单元
     */
    private void acceptTag(TemplateToken token) {
        String content = token.getContent();
        int nameStart = 0;
        while (nameStart < content.length() && Character.isWhitespace(content.charAt(nameStart))) {
            nameStart++;
        }
        int nameEnd = nameStart;
        while (nameEnd < content.length() && !Character.isWhitespace(content.charAt(nameEnd))) {
            nameEnd++;
        }
        String name = content.substring(nameStart, nameEnd);
        TagArguments arguments = new TagArguments(name, content.substring(nameEnd),
            token.getContentPosition().advance(content, 0, nameEnd));
        switch (name) {
            case "#if" -> openIf(token, arguments);
            case "#elseif" -> nextBranch(token, arguments, true);
            case "#else" -> nextBranch(token, arguments, false);
            case "#each" -> openEach(token, arguments);
            case "#join" -> openJoin(token, arguments);
            case "#part" -> openPart(token, arguments);
            case "/if", "/each", "/join", "/part" -> close(token, name.substring(1), arguments);
            default -> throw new TemplateCheckException(token.getPosition(), "未知标签：{{" + content.strip() + "}}");
        }
    }

    /**
     * 打开 if 块。
     *
     * @param token 标签单元
     * @param arguments 条件
     */
    private void openIf(TemplateToken token, TagArguments arguments) {
        OpenBlock block = new OpenBlock(IF, token.getPosition(), TemplateParser::buildIf);
        block.branchPosition = token.getPosition();
        block.branchCondition = new ExpressionParser(arguments).parseAll();
        stack.push(block);
    }

    /**
     * 进入 #elseif 或 #else 分支。
     *
     * @param token 标签单元
     * @param arguments 条件；#else 必须为空
     * @param conditional true 表示 #elseif
     */
    private void nextBranch(TemplateToken token, TagArguments arguments, boolean conditional) {
        String tag = conditional ? "{{#elseif}}" : "{{#else}}";
        OpenBlock block = stack.peek();
        if (block == null || !IF.equals(block.name)) {
            throw new TemplateCheckException(token.getPosition(), tag + " 必须写在 {{#if}} 内");
        }
        if (block.elseSeen) {
            throw new TemplateCheckException(token.getPosition(), tag + " 不能出现在 {{#else}} 之后");
        }
        TemplateExpression condition = conditional ? new ExpressionParser(arguments).parseAll() : null;
        if (!conditional) {
            arguments.expectEnd();
        }
        block.branches.add(new IfBranch(block.branchPosition, block.branchCondition, List.copyOf(block.children)));
        block.children = new ArrayList<>();
        block.branchPosition = token.getPosition();
        block.branchCondition = condition;
        block.elseSeen = !conditional;
    }

    /**
     * 关闭 if 块时组装节点。
     *
     * @param block 块
     * @return 条件节点
     */
    private static TemplateNode buildIf(OpenBlock block) {
        block.branches.add(new IfBranch(block.branchPosition, block.branchCondition, List.copyOf(block.children)));
        return new IfNode(block.position, List.copyOf(block.branches));
    }

    /**
     * 打开 each 块：path as item[, index] [separator="…"]。
     *
     * @param token 标签单元
     * @param arguments 参数
     */
    private void openEach(TemplateToken token, TagArguments arguments) {
        TemplatePath path = ExpressionParser.path(arguments.expect(TagToken.Kind.WORD, "集合路径"));
        TagToken as = arguments.expect(TagToken.Kind.WORD, " as");
        if (!as.isWord("as")) {
            throw arguments.unexpected(as, " as");
        }
        String item = variableName(arguments.expect(TagToken.Kind.WORD, "元素变量名"));
        String index = null;
        if (arguments.peek() != null && arguments.peek().isSymbol(",")) {
            arguments.next("");
            index = variableName(arguments.expect(TagToken.Kind.WORD, "下标变量名"));
        }
        String separator = optionalOption(arguments, "separator");
        arguments.expectEnd();
        String indexName = index;
        stack.push(new OpenBlock("each", token.getPosition(), block -> new EachNode(block.position, path, item,
            indexName, separator == null ? "" : separator, List.copyOf(block.children))));
    }

    /**
     * 打开 join 块："分隔符" [prefix="…"] [suffix="…"]，选项顺序不限、不可重复。
     *
     * @param token 标签单元
     * @param arguments 参数
     */
    private void openJoin(TemplateToken token, TagArguments arguments) {
        String separator = arguments.expect(TagToken.Kind.STRING, "分隔符字符串").getText();
        String prefix = null;
        String suffix = null;
        while (!arguments.atEnd()) {
            TagToken key = arguments.peek();
            if (key.isWord("prefix") && prefix == null) {
                prefix = optionalOption(arguments, "prefix");
            } else if (key.isWord("suffix") && suffix == null) {
                suffix = optionalOption(arguments, "suffix");
            } else {
                throw arguments.unexpected(key, " prefix= 或 suffix=");
            }
        }
        String joinPrefix = prefix == null ? "" : prefix;
        String joinSuffix = suffix == null ? "" : suffix;
        stack.push(new OpenBlock(JOIN, token.getPosition(),
            block -> buildJoin(block, separator, joinPrefix, joinSuffix)));
    }

    /**
     * 读取可选的 name="…" 选项。
     *
     * @param arguments 参数
     * @param name 选项名
     * @return 选项值；没有更多参数时为 null
     */
    private static String optionalOption(TagArguments arguments, String name) {
        if (arguments.atEnd()) {
            return null;
        }
        TagToken key = arguments.expect(TagToken.Kind.WORD, " " + name + "=");
        if (!key.isWord(name)) {
            throw arguments.unexpected(key, " " + name + "=");
        }
        arguments.expectSymbol("=");
        return arguments.expect(TagToken.Kind.STRING, " " + name + " 的字符串值").getText();
    }

    /**
     * 关闭 join 块时检查子节点并组装节点。
     *
     * @param block 块
     * @param separator 分隔符
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 拼接节点
     */
    private static TemplateNode buildJoin(OpenBlock block, String separator, String prefix, String suffix) {
        for (TemplateNode child : block.children) {
            if (child instanceof TextNode text && !text.getText().isBlank()) {
                throw new TemplateCheckException(firstVisible(text), JOIN_CHILD_MESSAGE);
            }
            if (child instanceof ValueNode || child instanceof FragmentNode || child instanceof JoinNode) {
                throw new TemplateCheckException(child.getPosition(), JOIN_CHILD_MESSAGE);
            }
        }
        return new JoinNode(block.position, separator, prefix, suffix, List.copyOf(block.children));
    }

    /**
     * 文本中第一个非空白字符的位置，用于把 join 内的多余正文定位到可见内容。
     *
     * @param text 文本节点
     * @return 位置
     */
    private static SourcePosition firstVisible(TextNode text) {
        String value = text.getText();
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return text.getPosition().advance(value, 0, index);
    }

    /**
     * 打开 part 块；只能直接写在 join 内。
     *
     * @param token 标签单元
     * @param arguments 参数，必须为空
     */
    private void openPart(TemplateToken token, TagArguments arguments) {
        arguments.expectEnd();
        OpenBlock parent = stack.peek();
        if (parent == null || !JOIN.equals(parent.name)) {
            throw new TemplateCheckException(token.getPosition(), "{{#part}} 只能直接写在 {{#join}} 内");
        }
        stack.push(new OpenBlock("part", token.getPosition(),
            block -> new PartNode(block.position, List.copyOf(block.children))));
    }

    /**
     * 关闭块；与最近打开的块不匹配时报错。
     *
     * @param token 标签单元
     * @param name 块名
     * @param arguments 参数，必须为空
     */
    private void close(TemplateToken token, String name, TagArguments arguments) {
        arguments.expectEnd();
        OpenBlock block = stack.peek();
        if (block == null) {
            throw new TemplateCheckException(token.getPosition(), "{{/" + name + "}} 没有对应的开始标签");
        }
        if (!block.name.equals(name)) {
            throw new TemplateCheckException(token.getPosition(), "标签不成对：" + block.position.describe()
                + "打开的 {{#" + block.name + "}} 应以 {{/" + block.name + "}} 结束，遇到 {{/" + name + "}}");
        }
        stack.pop();
        add(block.builder.apply(block));
    }

    /**
     * 把节点加入当前块或顶层。
     *
     * @param node 节点
     */
    private void add(TemplateNode node) {
        if (stack.isEmpty()) {
            root.add(node);
        } else {
            stack.peek().children.add(node);
        }
    }

    /**
     * 解析 #{…} 中的路径。
     *
     * @param token 占位单元
     * @return 路径
     */
    private static TemplatePath valuePath(TemplateToken token) {
        TemplatePath path = TemplatePath.parse(token.getContent());
        if (path == null) {
            throw new TemplateCheckException(token.getPosition(), "#{} 内的路径不合法：" + token.getContent());
        }
        return path;
    }

    /**
     * 解析 @{…} 中的片段参数名。
     *
     * @param token 占位单元
     * @return 名称
     */
    private static String fragmentName(TemplateToken token) {
        if (!TemplatePath.isName(token.getContent())) {
            throw new TemplateCheckException(token.getPosition(), "@{} 内的片段参数名不合法：" + token.getContent());
        }
        return token.getContent();
    }

    /**
     * 校验循环变量名。
     *
     * @param token 单词
     * @return 名称
     */
    private static String variableName(TagToken token) {
        if (!TemplatePath.isName(token.getText()) || token.getText().startsWith("__")) {
            throw new TemplateCheckException(token.getPosition(), "循环变量名不合法：" + token.getText());
        }
        return token.getText();
    }
}
