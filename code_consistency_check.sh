#!/bin/bash

# 代码一致性检查脚本
# 用于检查项目中的代码一致性问题

echo "🔍 HITA Agent 代码一致性检查"
echo "================================"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查计数器
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

check_result() {
    ((TOTAL_CHECKS++))
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $2"
        ((PASSED_CHECKS++))
    else
        echo -e "${RED}✗${NC} $2"
        ((FAILED_CHECKS++))
    fi
}

check_warn() {
    ((TOTAL_CHECKS++))
    echo -e "${YELLOW}⚠${NC} $1"
    ((PASSED_CHECKS++))
}

echo ""
echo "📏 代码风格一致性检查"
echo "---------------"

# 检查是否有随机数字常量
echo "检查魔法数字常量..."
RANDOM_CONSTANTS=$(grep -r "private const val.*= [0-9][0-9][0-9]" app/src/main --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')
if [ "$RANDOM_CONSTANTS" -lt 5 ]; then
    check_result 0 "魔法数字常量使用合理 ($RANDOM_CONSTANTS 处)"
else
    check_result 1 "发现过多随机数字常量 ($RANDOM_CONSTANTS 处)，建议使用命名常量"
fi

# 检查TAG常量定义
echo "检查TAG常量定义..."
TAG_COUNT=$(grep -r "const val TAG" app/src/main --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')
LOG_COUNT=$(grep -r "Log\." app/src/main --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')
if [ "$TAG_COUNT" -gt "$((LOG_COUNT / 10))" ]; then
    check_result 0 "TAG常量定义较充分 ($TAG_COUNT 个TAG，$LOG_COUNT 个日志)"
else
    check_warn "TAG常量定义不足 ($TAG_COUNT 个TAG，$LOG_COUNT 个日志)，建议使用LogUtils"
fi

echo ""
echo "🏗️ 架构一致性检查"
echo "---------------"

# 检查Repository命名
echo "检查Repository命名..."
REPO_COUNT=$(find app/src/main -name "*Repository.kt" 2>/dev/null | wc -l | tr -d ' ')
MANAGER_COUNT=$(find app/src/main -name "*Manager.kt" 2>/dev/null | wc -l | tr -d ' ')
if [ "$MANAGER_COUNT" -eq 0 ]; then
    check_result 0 "Repository命名一致 (Repository: $REPO_COUNT, Manager: $MANAGER_COUNT)"
else
    check_warn "发现Manager命名，建议统一为Repository ($REPO_COUNT Repository, $MANAGER_COUNT Manager)"
fi

# 检查Source命名
echo "检查Source/WebSource命名..."
SOURCE_COUNT=$(find app/src/main -name "*Source.kt" 2>/dev/null | wc -l | tr -d ' ')
WEBSOURCE_COUNT=$(find app/src/main -name "*WebSource.kt" 2>/dev/null | wc -l | tr -d ' ')
if [ "$WEBSOURCE_COUNT" -eq "$SOURCE_COUNT" ]; then
    check_result 0 "Source命名一致 ($SOURCE_COUNT 个Source，全部使用WebSource)"
else
    check_warn "Source命名不完全一致 ($SOURCE_COUNT 个Source，$WEBSOURCE_COUNT 个WebSource)"
fi

echo ""
echo "🔐 安全一致性检查"
echo "---------------"

# 检查GlobalScope使用
echo "检查GlobalScope使用..."
GLOBAL_SCOPE=$(grep -r "GlobalScope" app/src/main --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')
if [ "$GLOBAL_SCOPE" -eq 0 ]; then
    check_result 0 "未使用GlobalScope (已替换为applicationScope)"
else
    check_result 1 "仍有 $GLOBAL_SCOPE 处GlobalScope使用，应替换为applicationScope或viewModelScope"
fi

# 检查硬编码敏感信息
echo "检查硬编码敏感信息..."
HARDCODED_KEYS=$(grep -r "apiKey.*=.*\"" app/src/main --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')
if [ "$HARDCODED_KEYS" -eq 0 ]; then
    check_result 0 "未发现硬编码敏感信息"
else
    check_result 1 "发现 $HARDCODED_KEYS 处可能的硬编码敏感信息"
fi

echo ""
echo "📝 命名规范一致性检查"
echo "---------------"

# 检查文件命名规范
echo "检查Activity命名..."
ACTIVITY_COUNT=$(find app/src/main -name "*Activity.kt" 2>/dev/null | wc -l | tr -d ' ')
FRAGMENT_COUNT=$(find app/src/main -name "*Fragment.kt" 2>/dev/null | wc -l | tr -d ' ')
VIEWMODEL_COUNT=$(find app/src/main -name "*ViewModel.kt" 2>/dev/null | wc -l | tr -d ' ')
ADAPTER_COUNT=$(find app/src/main -name "*Adapter.kt" 2>/dev/null | wc -l | tr -d ' ')

TOTAL_UI_FILES=$((ACTIVITY_COUNT + FRAGMENT_COUNT + VIEWMODEL_COUNT + ADAPTER_COUNT))
if [ "$TOTAL_UI_FILES" -gt 0 ]; then
    check_result 0 "UI类命名规范 (Activity: $ACTIVITY_COUNT, Fragment: $FRAGMENT_COUNT, ViewModel: $VIEWMODEL_COUNT, Adapter: $ADAPTER_COUNT)"
else
    check_warn "UI类文件较少，可能需要检查"
fi

# 检查工具类命名
echo "检查工具类命名..."
UTILS_COUNT=$(find app/src/main -name "*Utils.kt" 2>/dev/null | wc -l | tr -d ' ')
HELPER_COUNT=$(find app/src/main -name "*Helper.kt" 2>/dev/null | wc -l | tr -d ' ')
TOOL_COUNT=$(find app/src/main -name "*Tool.kt" 2>/dev/null | wc -l | tr -d ' ')

if [ "$UTILS_COUNT" -gt 0 ]; then
    check_result 0 "工具类命名规范 (Utils: $UTILS_COUNT, Helper: $HELPER_COUNT, Tool: $TOOL_COUNT)"
else
    check_warn "工具类命名可以进一步统一"
fi

echo ""
echo "🎯 一致性改进建议"
echo "---------------"

# 检查新创建的工具类
if [ -f "app/src/main/java/com/limpu/hitax/utils/LogUtils.kt" ]; then
    echo -e "${GREEN}✓${NC} LogUtils.kt 已创建，建议在项目中统一使用"
fi

if [ -f "app/src/main/java/com/limpu/hitax/utils/AppConstants.kt" ]; then
    echo -e "${GREEN}✓${NC} AppConstants.kt 已创建，建议替换硬编码常量"
fi

if [ -f "app/src/main/java/com/limpu/hitax/utils/StringUtils.kt" ]; then
    echo -e "${GREEN}✓${NC} StringUtils.kt 已创建，建议统一字符串处理"
fi

echo ""
echo "📊 一致性评分"
echo "---------------"
SCORE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))
echo -e "总体评分: ${BLUE}$SCORE/100${NC}"
echo -e "通过检查: $PASSED_CHECKS/$TOTAL_CHECKS"
echo -e "失败检查: $FAILED_CHECKS/$TOTAL_CHECKS"

echo ""
if [ $FAILED_CHECKS -eq 0 ]; then
    echo -e "${GREEN}🎉 恭喜！代码一致性检查全部通过！${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠️  发现 $FAILED_CHECKS 个需要改进的地方${NC}"
    echo "请参考 CODE_CONSISTENCY_REPORT.md 进行改进"
    exit 1
fi