FROM nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c

# dist 由宿主机 npm run build 生成，镜像构建不安装依赖。
COPY deploy/nginx.conf /etc/nginx/nginx.conf
COPY dist/ /usr/share/nginx/client-console/

USER nginx
EXPOSE 8080
STOPSIGNAL SIGQUIT

# 静态配置无需官方入口脚本改写 /etc/nginx，兼容只读根目录。
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
