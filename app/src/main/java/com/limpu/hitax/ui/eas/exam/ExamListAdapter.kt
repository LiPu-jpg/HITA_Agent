package com.limpu.hitax.ui.eas.exam

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.limpu.hitax.data.model.eas.ExamItem
import com.limpu.hitax.databinding.ActivityEasExamBinding
import com.limpu.hitax.databinding.ActivityEasExamListItemBinding
import com.limpu.hitax.ui.eas.classroom.EmptyClassroomViewModel
import com.limpu.style.base.BaseListAdapter
import com.limpu.style.base.BaseViewHolder


@SuppressLint("ParcelCreator")
class ExamListAdapter (
        mContext: Context,
        mBeans: MutableList<ExamItem>,
    ):
    BaseListAdapter<ExamItem, ExamListAdapter.SHolder>(
        mContext, mBeans
    ) {
    class SHolder(viewBinding: ActivityEasExamListItemBinding) :
        BaseViewHolder<ActivityEasExamListItemBinding>(viewBinding)
    override fun bindHolder(
        holder: ExamListAdapter.SHolder,
        data: ExamItem?,
        position: Int
    ) {
        if (position == mBeans.size - 1) {
            holder.binding.divider2.visibility = View.GONE
        } else {
            holder.binding.divider2.visibility = View.VISIBLE
        }

        // 设置课程名称
        holder.binding.title.text = data?.courseName ?: "未知课程"

        // 设置考试类型
        // 本部（BENBU）有明确的期中期末分类，需要显示
        // 深圳校区所有考试都是"期末"，没有实际分类意义，不显示
        val examType = data?.examType ?: ""
        val campusName = data?.campusName ?: ""
        val shouldShow = when {
            campusName.contains("本部") -> true // 本部显示期中期末
            examType.isNotEmpty() && examType != "期末" -> true // 其他校区有非期末类型时显示
            else -> false
        }

        if (shouldShow) {
            holder.binding.examType.visibility = View.VISIBLE
            holder.binding.examType.text = examType
        } else {
            holder.binding.examType.visibility = View.GONE
        }

        // 设置日期和时间
        val date = data?.examDate ?: ""
        val time = data?.examTime ?: ""
        holder.binding.dateTime.text = if (time.isNotEmpty()) {
            "$date $time"
        } else {
            date
        }

        // 设置地点
        holder.binding.location.text = data?.examLocation ?: "地点待定"

        holder.binding.item.setOnClickListener{view -> mOnItemClickListener?.onItemClick(data, view, position)}
        holder.binding.item.setOnLongClickListener { view ->
            mOnItemLongClickListener?.onItemLongClick(data, view, position) ?: false
        }
    }

    override fun createViewHolder(
        viewBinding: ViewBinding,
        viewType: Int
    ): ExamListAdapter.SHolder {
        return ExamListAdapter.SHolder(viewBinding = viewBinding as ActivityEasExamListItemBinding)
    }

    override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
        return ActivityEasExamListItemBinding.inflate(mInflater,parent,false)
    }
}
