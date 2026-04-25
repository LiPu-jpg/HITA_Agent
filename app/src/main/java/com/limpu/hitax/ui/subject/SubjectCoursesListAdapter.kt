package com.limpu.hitax.ui.subject

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.viewbinding.ViewBinding
import com.limpu.hitax.data.model.timetable.EventItem
import com.limpu.hitax.databinding.DynamicSubjectCourseitemBinding
import com.limpu.hitax.databinding.DynamicSubjectCourseitemPassedBinding
import com.limpu.hitax.databinding.DynamicSubjectCourseitemTagBinding
import com.limpu.style.base.BaseCheckableListAdapter
import com.limpu.style.base.BaseViewHolder
import com.limpu.hitax.utils.TimeTools
import java.util.*

@SuppressLint("ParcelCreator")
class SubjectCoursesListAdapter(context: Context, list: MutableList<EventItem>) :
        BaseCheckableListAdapter<EventItem, BaseViewHolder<*>>(
                context,
                list
        ) {

    override fun getItemViewType(position: Int): Int {
        val item = mBeans[position]
        return when {
            item.type === EventItem.TYPE.TAG -> VIEW_TYPE_TAG
            TimeTools.passed(item.to) -> VIEW_TYPE_PASSED
            else -> VIEW_TYPE_NORMAL
        }
    }

    override fun bindHolder(holder: BaseViewHolder<*>, data: EventItem?, position: Int) {
        super.bindHolder(holder, data, position)
        val c = Calendar.getInstance()
        data?.from?.time?.let {
            c.timeInMillis = it
        }
        if (holder is TagViewHolder) {
            if (data?.name == "more") {
                holder.binding.icon.rotation = 0f
            } else {
                holder.binding.icon.rotation = 180f
            }
            holder.binding.item.setOnClickListener {
                data?.let { it1 ->
                    mOnItemClickListener?.onItemClick(
                            it1, it, position)
                }
            }
        } else if (holder is NormalViewHolder) {
            holder.binding.subjectCourselistMonth.text =
                    TimeTools.getDateString(mContext, c, true, TimeTools.TTY_REPLACE)
            if (isEditMode) holder.binding.icon.visibility = GONE
            else holder.binding.icon.visibility = VISIBLE
            holder.binding.item.setOnClickListener {
                data?.let { it1 ->
                    if(isEditMode){
                        holder.toggleCheck()
                    }else{
                        mOnItemClickListener?.onItemClick(
                            it1, it, position)
                    }

                }
            }
        } else if (holder is PassedViewHolder) {
            holder.binding.subjectCourselistMonth.text =
                    TimeTools.getDateString(mContext, c, true, TimeTools.TTY_REPLACE)
            if (isEditMode) holder.binding.icon.visibility = GONE
            else holder.binding.icon.visibility = VISIBLE
            holder.binding.item.setOnClickListener {
                data?.let { it1 ->
                    if(isEditMode){
                        holder.toggleCheck()
                    }else {
                        mOnItemClickListener?.onItemClick(
                            it1, it, position
                        )
                    }
                }
            }
        }

    }


    class NormalViewHolder(viewBinding: DynamicSubjectCourseitemBinding) :
            BaseViewHolder<DynamicSubjectCourseitemBinding>(
                    viewBinding
            ), CheckableViewHolder {
        override fun showCheckBox() {
            binding.check.visibility = VISIBLE
        }

        override fun hideCheckBox() {
            binding.check.visibility = GONE
        }

        override fun toggleCheck() {
            binding.check.toggle()
        }

        override fun setChecked(boolean: Boolean) {
            binding.check.isChecked = boolean
        }

        override fun setInternalOnLongClickListener(listener: View.OnLongClickListener) {
            binding.item.setOnLongClickListener(listener)
        }

        override fun setInternalOnClickListener(listener: View.OnClickListener) {
            binding.item.setOnClickListener (listener)
        }

        override fun setInternalOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
            binding.check.setOnCheckedChangeListener(listener)
        }
    }

    class PassedViewHolder(viewBinding: DynamicSubjectCourseitemPassedBinding) :
            BaseViewHolder<DynamicSubjectCourseitemPassedBinding>(
                    viewBinding
            ), CheckableViewHolder {
        override fun showCheckBox() {
            binding.check.visibility = VISIBLE
        }

        override fun hideCheckBox() {
            binding.check.visibility = GONE
        }

        override fun toggleCheck() {
            binding.check.toggle()
        }

        override fun setChecked(boolean: Boolean) {
            binding.check.isChecked = boolean
        }

        override fun setInternalOnLongClickListener(listener: View.OnLongClickListener) {
            binding.item.setOnLongClickListener(listener)
        }

        override fun setInternalOnClickListener(listener: View.OnClickListener) {
            binding.item.setOnClickListener(listener)
        }

        override fun setInternalOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
            binding.check.setOnCheckedChangeListener(listener)
        }
    }

    class TagViewHolder(viewBinding: DynamicSubjectCourseitemTagBinding) :
            BaseViewHolder<DynamicSubjectCourseitemTagBinding>(
                    viewBinding
            )

    companion object {
        private const val VIEW_TYPE_NORMAL = 1
        private const val VIEW_TYPE_PASSED = 2
        private const val VIEW_TYPE_TAG = 3
    }

    override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
        return when (viewType) {
            VIEW_TYPE_NORMAL -> DynamicSubjectCourseitemBinding.inflate(mInflater, parent, false)
            VIEW_TYPE_PASSED -> DynamicSubjectCourseitemPassedBinding.inflate(mInflater, parent, false)
            VIEW_TYPE_TAG -> DynamicSubjectCourseitemTagBinding.inflate(mInflater, parent, false)
            else -> DynamicSubjectCourseitemPassedBinding.inflate(mInflater, parent, false)
        }
    }

    override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): BaseViewHolder<*> {
        return when (viewBinding) {
            is DynamicSubjectCourseitemBinding -> NormalViewHolder(viewBinding)
            is DynamicSubjectCourseitemPassedBinding -> PassedViewHolder(viewBinding)
            else -> TagViewHolder(viewBinding as DynamicSubjectCourseitemTagBinding)
        }
    }

}