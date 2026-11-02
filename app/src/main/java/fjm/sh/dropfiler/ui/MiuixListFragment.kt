package fjm.sh.dropfiler.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hchen.himiuix.fragment.Fragment
import fjm.sh.dropfiler.R

/**
 * A scrolling list that is linked to the activity's MiuixAppBar (large title collapses on
 * scroll). Miuix XML requires the content of a MiuixAppBar to be hosted by one of its
 * Fragment classes. The activity provides the adapter through [Host].
 */
class MiuixListFragment : Fragment() {

	/**
	 * Implemented by activities that host this fragment.
	 */
	interface Host {
		fun onListViewCreated(recyclerView: RecyclerView)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
		inflater.inflate(R.layout.fs_fragment_list, container, false)

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val recyclerView = view.findViewById<RecyclerView>(R.id.list)
		recyclerView.layoutManager = LinearLayoutManager(view.context)
		recyclerView.itemAnimator = null
		(activity as? Host)?.onListViewCreated(recyclerView)
	}
}
