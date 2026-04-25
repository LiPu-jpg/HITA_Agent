#!/bin/bash

# HITA Agent 项目健康检查脚本

echo "🔍 HITA Agent 项目健康检查"
echo "================================"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查计数器
PASS=0
FAIL=0
WARN=0

# 检查函数
check_pass() {
    echo -e "${GREEN}✓${NC} $1"
    ((PASS++))
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    ((FAIL++))
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
    ((WARN++))
}

echo ""
echo "📁 项目结构检查"
echo "---------------"

# 检查主要目录
if [ -d "app/src/main" ]; then
    check_pass "主应用源码目录存在"
else
    check_fail "主应用源码目录缺失"
fi

if [ -d "component/src/main" ]; then
    check_pass "组件模块源码目录存在"
else
    check_fail "组件模块源码目录缺失"
fi

# 检查gradle文件
if [ -f "build.gradle" ]; then
    check_pass "根级build.gradle存在"
else
    check_fail "根级build.gradle缺失"
fi

if [ -f "app/build.gradle" ]; then
    check_pass "应用级build.gradle存在"
else
    check_fail "应用级build.gradle缺失"
fi

echo ""
echo "🔧 构建系统检查"
echo "---------------"

# 检查gradle wrapper
if [ -f "gradlew" ]; then
    check_pass "Gradle wrapper存在"
    if [ -x "gradlew" ]; then
        check_pass "Gradle wrapper可执行"
    else
        check_warn "Gradle wrapper不可执行，运行: chmod +x gradlew"
    fi
else
    check_fail "Gradle wrapper缺失"
fi

# 检查gradle版本
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    GRADLE_VERSION=$(grep "gradle" gradle/wrapper/gradle-wrapper.properties | cut -d'=' -f2 | tr -d ' ')
    check_pass "Gradle版本: $GRADLE_VERSION"
else
    check_warn "无法确定Gradle版本"
fi

echo ""
echo "📝 文档检查"
echo "---------------"

# 检查文档文件
if [ -f "README.md" ]; then
    check_pass "README.md存在"
else
    check_warn "README.md缺失"
fi

if [ -f "CODING_STANDARDS.md" ]; then
    check_pass "编码规范文档存在"
else
    check_warn "编码规范文档缺失"
fi

if [ -f "README_DEV.md" ]; then
    check_pass "开发指南存在"
else
    check_warn "开发指南缺失"
fi

if [ -f "REFACTORING_REPORT.md" ]; then
    check_pass "修缮报告存在"
else
    check_warn "修缮报告缺失"
fi

echo ""
echo "🔒 代码质量检查"
echo "---------------"

# 统计Kotlin文件
KT_FILES=$(find . -name "*.kt" -not -path "./build/*" -not -path "./.gradle/*" | wc -l | tr -d ' ')
if [ $KT_FILES -gt 0 ]; then
    check_pass "找到 $KT_FILES 个Kotlin源文件"
else
    check_fail "未找到Kotlin源文件"
fi

# 检查全局作用域使用
GLOBAL_SCOPE_COUNT=$(grep -r "GlobalScope" --include="*.kt" . 2>/dev/null | wc -l | tr -d ' ')
if [ $GLOBAL_SCOPE_COUNT -eq 0 ]; then
    check_pass "未发现GlobalScope使用"
else
    check_warn "发现 $GLOBAL_SCOPE_COUNT 处GlobalScope使用（应替换为applicationScope）"
fi

# 检查硬编码URL
HARDCODED_URL=$(grep -r "\"http://" --include="*.kt" . 2>/dev/null | wc -l | tr -d ' ')
if [ $HARDCODED_URL -lt 10 ]; then
    check_pass "硬编码URL数量较少 ($HARDCODED_URL 处)"
else
    check_warn "发现 $HARDCODED_URL 处硬编码URL（建议提取为常量）"
fi

# 检查TODO注释
TODO_COUNT=$(grep -r "TODO\|FIXME" --include="*.kt" . 2>/dev/null | wc -l | tr -d ' ')
if [ $TODO_COUNT -lt 5 ]; then
    check_pass "TODO/FIXME注释较少 ($TODO_COUNT 处)"
else
    check_warn "发现 $TODO_COUNT 处TODO/FIXME注释"
fi

echo ""
echo "🛠️ 工具类检查"
echo "---------------"

# 检查自定义工具类
if [ -f "app/src/main/java/com/limpu/hitax/utils/NullSafetyExtensions.kt" ]; then
    check_pass "空安全扩展工具类存在"
else
    check_warn "空安全扩展工具类缺失"
fi

if [ -f "app/src/main/java/com/limpu/hitax/utils/ResourceCleaner.kt" ]; then
    check_pass "资源清理工具类存在"
else
    check_warn "资源清理工具类缺失"
fi

if [ -f "app/src/main/java/com/limpu/hitax/utils/PerformanceUtils.kt" ]; then
    check_pass "性能优化工具类存在"
else
    check_warn "性能优化工具类缺失"
fi

echo ""
echo "📊 检查结果统计"
echo "---------------"
echo -e "${GREEN}通过: $PASS${NC}"
echo -e "${YELLOW}警告: $WARN${NC}"
echo -e "${RED}失败: $FAIL${NC}"

echo ""
echo "💡 改进建议"
echo "---------------"

if [ $FAIL -gt 0 ]; then
    echo "1. 修复上述失败的检查项"
fi

if [ $GLOBAL_SCOPE_COUNT -gt 0 ]; then
    echo "2. 将GlobalScope替换为applicationScope或viewModelScope"
fi

if [ $HARDCODED_URL -ge 10 ]; then
    echo "3. 提取硬编码URL到常量或配置文件"
fi

if [ $TODO_COUNT -ge 5 ]; then
    echo "4. 处理或更新TODO/FIXME注释"
fi

echo ""
echo "✅ 项目健康检查完成！"

# 返回退出码
if [ $FAIL -eq 0 ]; then
    exit 0
else
    exit 1
fi
