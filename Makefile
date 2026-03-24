SHELL := /bin/zsh

JAVA_HOME := /Users/songyangyang/Library/Java/JavaVirtualMachines/corretto-19.0.2/Contents/Home
MAVEN := JAVA_HOME=$(JAVA_HOME) mvn

.PHONY: help infra-up infra-down infra-logs compile app gateway scheduler health verify scan-legacy

help:
	@echo "AIP 开发命令："
	@echo "  make infra-up      启动基础设施(PostgreSQL/Redis/Nacos)"
	@echo "  make infra-down    停止基础设施"
	@echo "  make infra-logs    查看基础设施日志"
	@echo "  make compile       编译 aip-app 及依赖模块"
	@echo "  make app           启动 aip-app (8081)"
	@echo "  make gateway       启动 aip-gateway (8079)"
	@echo "  make scheduler     启动 aip-scheduler (8085)"
	@echo "  make health        快速检查 app/gateway/scheduler 健康状态"
	@echo "  make verify        全模块编译检查（含 gateway/scheduler/app）"
	@echo "  make scan-legacy   扫描旧命名残留（自动排除 logs/node_modules/dist）"

infra-up:
	docker compose up -d

infra-down:
	docker compose down

infra-logs:
	docker compose logs -f

compile:
	$(MAVEN) -pl aip-app -am -DskipTests compile

app:
	$(MAVEN) -pl aip-app -am -DskipTests spring-boot:run

gateway:
	$(MAVEN) -pl aip-gateway -DskipTests spring-boot:run

scheduler:
	$(MAVEN) -pl aip-scheduler -am -DskipTests spring-boot:run

health:
	@echo "aip-app:"
	@curl -s http://localhost:8081/actuator/health || true
	@echo "\n\naip-gateway:"
	@curl -s http://localhost:8079/actuator/health || true
	@echo "\n\naip-scheduler:"
	@curl -s http://localhost:8085/actuator/health || true
	@echo ""

verify:
	$(MAVEN) -DskipTests compile

scan-legacy:
	@echo "扫描旧命名残留（eap/EAP/com.enterprise.agent）..."
	@git grep -nE "com\\.enterprise\\.agent|\\beap\\b|\\bEAP\\b|\\beap-|\\beap\\." -- \
		. \
		':(exclude)logs/**' \
		':(exclude)admin-frontend/node_modules/**' \
		':(exclude)admin-frontend/dist/**' \
		':(exclude)**/__pycache__/**' || true
