package com.limpu.hitax.ui.eas.score

import android.view.View
import com.limpu.hitax.R
import com.limpu.hitax.data.model.eas.CourseScoreItem
import com.limpu.hitax.data.model.eas.TermItem
import com.limpu.hitax.databinding.DialogBottomScoresPickerBinding
import com.limpu.style.widgets.TransparentBottomSheetDialog

class ScoreDetailFragment(
    private val score: CourseScoreItem
): TransparentBottomSheetDialog<DialogBottomScoresPickerBinding>(){
    override fun getLayoutId(): Int {
        return R.layout.dialog_bottom_scores_picker
    }

    override fun initViewBinding(v: View): DialogBottomScoresPickerBinding {
        return DialogBottomScoresPickerBinding.bind(v)
    }

    override fun initViews(v: View) {
        binding.scoreCredits.text = score.credits.toString()
        binding.scoreAssessMethod.text = score.assessMethod
        binding.scoreCategory.text = score.courseCategory
        binding.scoreCode.text = score.courseCode
        binding.scoreHours.text = score.hours.toString()
        binding.scorePorperty.text = score.courseProperty
        binding.scoreSchoolName.text = score.schoolName
        binding.title.text = score.courseName
    }
}