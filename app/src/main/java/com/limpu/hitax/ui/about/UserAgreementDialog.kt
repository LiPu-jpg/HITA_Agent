package com.limpu.hitax.ui.about

import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import com.limpu.hitax.R
import com.limpu.hitax.databinding.DialogBottomUserAgreementBinding
import com.limpu.style.widgets.TransparentModeledBottomSheetDialog

@Suppress("DEPRECATION")
class UserAgreementDialog :
    TransparentModeledBottomSheetDialog<UserAgreementViewModel, DialogBottomUserAgreementBinding>() {

    var onResponseListener: OnResponseListener? = null
    private var showActionButtons = false

    interface OnResponseListener {
        fun onAgree()
        fun onRefuse()
    }

    fun setShowActionButtons(show: Boolean): UserAgreementDialog {
        showActionButtons = show
        return this
    }

    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_user_agreement
    }

    override fun initViewBinding(v: View): DialogBottomUserAgreementBinding {
        return DialogBottomUserAgreementBinding.bind(v)
    }

    override fun getViewModelClass(): Class<UserAgreementViewModel> {
        return UserAgreementViewModel::class.java
    }

    @Suppress("UNUSED_PARAMETER")
    override fun initViews(view: View) {
        val views: MutableList<ViewGroup?> = mutableListOf(null, null)
        val localUa = getString(R.string.user_agreement)
        val localPp = getString(R.string.privacy_policy)

        fun setViewText(position: Int, content: String) {
            (views[position]?.findViewById(R.id.text) as TextView?)?.text = Html.fromHtml(content)
        }

        val adapter = object : PagerAdapter() {
            override fun getCount(): Int {
                return 2
            }
            override fun getPageTitle(position: Int): CharSequence {
                return if (position == 0) {
                    getString(R.string.name_user_agreement)
                } else {
                    getString(R.string.name_privacy_agreement)
                }
            }

            override fun isViewFromObject(view: View, `object`: Any): Boolean {
                return view === `object`
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView(`object` as View)
                views[position] = null
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                if(views[position]==null) {
                    views[position] =
                        layoutInflater.inflate(R.layout.dynamic_user_agreement,container,false) as ViewGroup?
                    val param = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
                    views[position]?.layoutParams = param
                    container.addView(views[position])
                    setViewText(position, if (position == 0) localUa else localPp)
                }
                return views[position] as View
            }
        }
        binding?.tabs?.setupWithViewPager(binding?.pager)
        binding?.pager?.adapter = adapter

        if (showActionButtons || onResponseListener != null) {
            binding?.agreementActions?.visibility = View.VISIBLE
            binding?.buttonAgree?.setOnClickListener {
                onResponseListener?.onAgree()
                dismiss()
            }
            binding?.buttonRefuse?.setOnClickListener {
                onResponseListener?.onRefuse()
                dismiss()
            }
        } else {
            binding?.agreementActions?.visibility = View.GONE
        }
    }

    override fun isCancelable(): Boolean {
        return onResponseListener == null
    }
}
