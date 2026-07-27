package org.qosp.notes.ui.common

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.transition.MaterialSharedAxis
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.qosp.notes.ui.ActivityViewModel
import org.qosp.notes.ui.utils.ExportNotesContract

const val FRAGMENT_MESSAGE = "FRAGMENT_MESSAGE"

/**
 * Minimal base fragment for the retained Quillpad editor.
 *
 * This mirrors the public surface of Quillpad's original `BaseFragment`
 * (`activityModel`, `toolbar`, `hasDefaultAnimation`, `setupToolbar`,
 * `sendMessage`, `exportNotesLauncher`) so that
 * [org.qosp.notes.ui.editor.EditorFragment] compiles and runs unchanged.
 *
 * Unlike the original, it does not depend on Quillpad's `MainActivity`; it only
 * installs the fragment toolbar as the host action bar.
 */
open class BaseFragment(@LayoutRes resId: Int) : Fragment(resId) {

    protected open val hasMenu: Boolean = true
    protected open val hasDefaultAnimation: Boolean = true

    protected val TAG = this::class.simpleName ?: "Quillpad"

    val activityModel: ActivityViewModel by activityViewModel()
    protected open val toolbar: Toolbar? = null
    protected open val toolbarTitle: String = ""

    protected val exportNotesLauncher = registerForActivityResult(ExportNotesContract) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(hasMenu)
        if (hasDefaultAnimation) {
            enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply { duration = 300L }
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply { duration = 300L }
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false).apply { duration = 300L }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
    }

    protected open fun setupToolbar() {
        val currentToolbar = toolbar ?: return
        val host = activity as? AppCompatActivity
        if (host != null) {
            host.setSupportActionBar(currentToolbar)
            host.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            return
        }

        // KardLeaf's main Compose host is a FragmentActivity rather than an
        // AppCompatActivity. In embedded mode the toolbar therefore owns its
        // menu directly instead of delegating it to an Activity action bar.
        if (hasMenu) {
            currentToolbar.menu.clear()
            onCreateOptionsMenu(currentToolbar.menu, requireActivity().menuInflater)
            currentToolbar.setOnMenuItemClickListener { item -> onOptionsItemSelected(item) }
        }
        currentToolbar.setNavigationIcon(com.kangle.kardleaf.R.drawable.ic_quillpad_back)
    }

    protected fun sendMessage(message: String) {
        setFragmentResult(FRAGMENT_MESSAGE, bundleOf(FRAGMENT_MESSAGE to message))
    }
}
