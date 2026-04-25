package com.limpu.hitax.ui.eas.classroom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ActivityEasClassroomItemBinding
import com.limpu.style.base.BaseActivity
import com.limpu.style.base.BaseListAdapter
import com.limpu.style.base.BaseViewHolder
import com.limpu.hitax.utils.TimeTools

@SuppressLint("ParcelCreator")
class EmptyClassroomListAdapter(
    mContext: Context,
    mBeans: MutableList<ClassroomItem>,
    val viewModel: EmptyClassroomViewModel
) :
    BaseListAdapter<ClassroomItem, EmptyClassroomListAdapter.KHolder>(
        mContext, mBeans
    ) {


    private fun getState(data: ClassroomItem): String {
        viewModel.timetableStructureLiveData.value?.data?.let {
            val current = TimeTools.getCurrentScheduleNumber(it)
            val currentDow: Int = TimeTools.getDow(System.currentTimeMillis())
            val occupiedNumbers = data.scheduleList
                .filter { je ->
                    val dow = je.optInt("XQJ")
                    val num = je.optInt("XJ") * 10
                    val occupied = je.optString("JYBJ").isNotBlank() || je.optString("PKBJ").isNotBlank()
                    dow == currentDow && occupied
                }
                .map { je -> je.optInt("XJ") * 10 }
                .toSet()
            return when {
                current in occupiedNumbers -> "被占"
                current % 10 == 5 && (current + 5) in occupiedNumbers -> "将占"
                current % 10 == 0 && (current + 10) in occupiedNumbers -> "将占"
                else -> "空闲"
            }
        }
        return "未知"
    }

    class KHolder(itemView: ActivityEasClassroomItemBinding) :
        BaseViewHolder<ActivityEasClassroomItemBinding>(
            itemView
        )

    override fun getViewBinding(parent: ViewGroup, viewType: Int): ViewBinding {
        return ActivityEasClassroomItemBinding.inflate(mInflater, parent, false)
    }

    override fun createViewHolder(viewBinding: ViewBinding, viewType: Int): KHolder {
        return KHolder(viewBinding as ActivityEasClassroomItemBinding)
    }

    override fun bindHolder(holder: KHolder, data: ClassroomItem?, position: Int) {
        holder.binding.name.text = data?.name
        holder.binding.state.text = data?.let { getState(it) }
        if (holder.binding.state.text != "空闲") {
            holder.binding.state.setBackgroundResource(R.drawable.element_rounded_button_bg_grey)
            holder.binding.state.setTextColor(Color.GRAY)
        } else if (mContext is BaseActivity<*, *>) {
            holder.binding.state.setBackgroundResource(R.drawable.element_rounded_button_bg_primary_light)
            holder.binding.state.setTextColor((mContext as BaseActivity<*, *>).getColorPrimary())
        }

        holder.binding.card.setOnClickListener { view ->
            mOnItemClickListener?.onItemClick(
                data,
                view,
                position
            )
        }
    }


}