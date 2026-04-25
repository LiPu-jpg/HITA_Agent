#!/bin/bash

# LogUtils 剩余日志调用自动迁移脚本
# 用于批量处理剩余的文件

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

SUCCESS_COUNT=0
SKIP_COUNT=0
ERROR_COUNT=0

print_success() {
    ((SUCCESS_COUNT++))
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    ((ERROR_COUNT++))
    echo -e "${RED}✗${NC} $1"
}

print_skip() {
    ((SKIP_COUNT++))
    echo -e "${YELLOW}⊘${NC} $1"
}

echo "🚀 LogUtils 剩余日志自动迁移"
echo "=============================="
echo ""

# 查找所有还使用 Log 的文件（排除 utils 包和已处理的文件）
find app/src/main/java -name "*.kt" -not -path "*/utils/*" -exec grep -l "Log\.[diwev]" {} \; | while read file; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        log_count=$(grep -c "Log\.[diwev]" "$file" 2>/dev/null || echo 0)

        echo "处理: $filename ($log_count 个日志调用)"

        # 检查是否已经有 LogUtils 导入
        has_import=$(grep -c "import com.limpu.hitax.utils.LogUtils" "$file" 2>/dev/null || echo 0)

        if [ "$has_import" -eq 0 ]; then
            # 添加 LogUtils 导入
            last_import_line=$(grep -n "^import " "$file" | tail -1 | cut -d: -f1)
            if [ -n "$last_import_line" ]; then
                sed -i '' "${last_import_line}a\\
import com.limpu.hitax.utils.LogUtils
" "$file" 2>/dev/null || print_error "添加导入失败: $filename"
            fi
        fi

        # 替换所有日志调用
        sed -i '' \
            -e 's/Log\.d(TAG,/LogUtils.d(/g' \
            -e 's/Log\.i(TAG,/LogUtils.i(/g' \
            -e 's/Log\.w(TAG,/LogUtils.w(/g' \
            -e 's/Log\.e(TAG,/LogUtils.e(/g' \
            -e 's/Log\.v(TAG,/LogUtils.v(/g' \
            -e 's/Log\.d("[^"]*",/LogUtils.d(/g' \
            -e 's/Log\.i("[^"]*",/LogUtils.i(/g' \
            -e 's/Log\.w("[^"]*",/LogUtils.w(/g' \
            -e 's/Log\.e("[^"]*",/LogUtils.e(/g' \
            -e 's/Log\.v("[^"]*",/LogUtils.v(/g' \
            -e 's/Log\.d($/LogUtils.d(/g' \
            -e 's/Log\.i($/LogUtils.i(/g' \
            -e 's/Log\.w($/LogUtils.w(/g' \
            -e 's/Log\.e($/LogUtils.e(/g' \
            -e 's/Log\.v($/LogUtils.v(/g' \
            "$file" 2>/dev/null || print_error "替换日志调用失败: $filename"

        # 删除 android.util.Log 导入
        sed -i '' '/^import android.util.Log$/d' "$file" 2>/dev/null

        # 删除独立的 TAG 行
        sed -i '' '/^[[:space:]]*TAG,$/d' "$file" 2>/dev/null

        # 修复 android.util.LogUtils 错误
        sed -i '' 's/android\.util\.LogUtils\./LogUtils./g' "$file" 2>/dev/null

        # 修复 LogUtils.w 中带异常的调用（改为 LogUtils.e）
        sed -i '' 's/LogUtils\.w(\([^,]*\), \([a-zA-Z_]*\))/LogUtils.e(\1, \2)/g' "$file" 2>/dev/null

        print_success "$filename: 迁移 $log_count 个日志调用"
    fi
done

echo ""
echo "============================"
echo "📊 迁移统计"
echo "============================"
echo -e "${GREEN}成功: $SUCCESS_COUNT${NC}"
echo -e "${YELLOW}跳过: $SKIP_COUNT${NC}"
echo -e "${RED}错误: $ERROR_COUNT${NC}"
echo ""
echo "✅ 迁移完成！"
echo ""
echo "📝 后续步骤:"
echo "1. 运行: ./gradlew compileDebugKotlin 验证编译"
echo "2. 运行: ./code_consistency_check.sh 检查一致性"
echo "3. 提交代码前进行测试"

exit 0
