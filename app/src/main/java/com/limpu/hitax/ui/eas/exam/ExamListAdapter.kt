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
        holder.binding.title.text = data?.courseName
        holder.binding.time.text = data?.examDate
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
