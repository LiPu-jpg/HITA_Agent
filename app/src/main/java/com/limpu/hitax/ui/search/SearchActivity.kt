package com.limpu.hitax.ui.search

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.limpu.hitax.R
import com.limpu.hitax.databinding.ActivitySearchBinding
import com.limpu.style.base.BaseActivity
import com.limpu.style.base.BaseTabAdapter
import com.limpu.hitax.ui.search.teacher.FragmentSearchTeacher
import com.limpu.style.base.FragmentSearchResult

class SearchActivity : BaseActivity<SearchViewModel, ActivitySearchBinding>(),
    BasicFragmentSearchResult.SearchRoot {
    var pagerAdapter: SearchPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setToolbarActionBack(binding.toolbar)
    }


    override fun onStart() {
        super.onStart()
        searchForPurpose()
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        if (!isSearchForPurpose()) {
            popUpKeyboard()
        }
    }


    private fun isSearchForPurpose(): Boolean {
        val text = intent.getStringExtra("keyword")
        val purpose = intent.getStringExtra("type")
        return text?.isNotEmpty() == true && purpose?.isNotEmpty() == true
    }

    private fun searchForPurpose() {
        val text = intent.getStringExtra("keyword")
        binding.searchview.setText(text)
        val purpose = intent.getStringExtra("type")
        if (text.isNullOrEmpty() || purpose.isNullOrEmpty()) return
        var index = 0
        when (purpose) {
            "TEACHER" -> index = 0
        }
        binding.pager.currentItem = index
        return
    }

    override fun initViews() {
        binding.toolbar.title = ""
        binding.searchview.setOnEditorActionListener (object : TextView.OnEditorActionListener {
            override fun onEditorAction(textView: TextView, i: Int, keyEvent: KeyEvent?): Boolean {
                if (textView.text.toString().isBlank()) return false
                if (i == EditorInfo.IME_ACTION_GO || i == EditorInfo.IME_ACTION_SEARCH) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                    setSearchText(getSearchText())
                    return true
                }
                return false
            }
        })
        pagerAdapter = SearchPagerAdapter(supportFragmentManager, this)
        binding.pager.adapter = pagerAdapter
        binding.tabs.setupWithViewPager(binding.pager)
    }


    @Suppress("DEPRECATION")
    private fun popUpKeyboard() {
        binding.searchview.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    override fun getSearchText(): String {
        return binding.searchview.text.toString()
    }

    fun setSearchText(text: String) {
        for (f in supportFragmentManager.fragments) {
            if (f is FragmentSearchResult) {
                f.setSearchText(text)
            }
        }
    }

    class SearchPagerAdapter(fm: FragmentManager, val context: Context) : BaseTabAdapter(fm, 1) {
        private var titles: IntArray = intArrayOf(
            R.string.tab_search_teacher
        )

        override fun getPageTitle(position: Int): CharSequence {
            return context.getString(titles[position])
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            mFragments[position] = null
        }

        override fun initItem(position: Int): Fragment {
            return FragmentSearchTeacher()
        }
    }

    override fun initViewBinding(): ActivitySearchBinding {
        return ActivitySearchBinding.inflate(layoutInflater)
    }

    override fun getViewModelClass(): Class<SearchViewModel> {
        return SearchViewModel::class.java
    }

}
