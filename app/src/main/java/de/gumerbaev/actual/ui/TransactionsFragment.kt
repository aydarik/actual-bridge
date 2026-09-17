package de.gumerbaev.actual.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import de.gumerbaev.actual.databinding.FragmentTransactionsBinding
import de.gumerbaev.actual.ui.adapter.HistoryAdapter

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter
    private val mainActivity: MainActivity
        get() = requireActivity() as MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            records = mutableListOf(),
            onResend = { record ->
                val intent = Intent(requireContext(), ConfirmTransactionActivity::class.java).apply {
                    putExtra(ConfirmTransactionActivity.EXTRA_TRANSACTION, record.transaction)
                    putExtra(ConfirmTransactionActivity.EXTRA_RECORD_ID, record.id)
                }
                startActivity(intent)
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupListeners() {
        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Transaction Log")
                .setMessage("Clear all logged transactions?")
                .setPositiveButton("Clear") { _, _ ->
                    mainActivity.prefs.clearHistory()
                    loadHistory()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    fun loadHistory() {
        if (_binding == null) return
        val history = mainActivity.prefs.getHistory()
        historyAdapter.updateData(history)
        binding.tvEmptyHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}