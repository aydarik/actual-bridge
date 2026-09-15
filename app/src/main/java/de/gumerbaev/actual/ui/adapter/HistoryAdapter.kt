package de.gumerbaev.actual.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.gumerbaev.actual.R
import de.gumerbaev.actual.databinding.ItemHistoryBinding
import de.gumerbaev.actual.model.ActualTransaction
import de.gumerbaev.actual.model.TransactionRecord
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class HistoryAdapter(
    private var records: MutableList<TransactionRecord>,
    private val onResend: (TransactionRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = records[position]
        val tx = record.transaction
        val context = holder.itemView.context

        with(holder.binding) {
            tvHistoryPayee.text = tx.payee
            tvHistoryLocationIndicator.visibility =
                if (tx.latitude != null && tx.longitude != null) View.VISIBLE else View.GONE

            val timeStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(record.timestamp))
            tvHistoryAccountDate.text = "${tx.account} • $timeStr"
            tvHistorySource.text = "from ${record.sourcePackage}"

            val isDeposit = tx.type == ActualTransaction.TYPE_DEPOSIT
            val amountFormatted = "%.2f".format(Locale.US, tx.amount?.let { abs(it) } ?: 0.0)

            if (isDeposit) {
                tvHistoryAmount.text = "+$amountFormatted"
                tvHistoryAmount.setTextColor(ContextCompat.getColor(context, R.color.secondary))
            } else {
                tvHistoryAmount.text = "-$amountFormatted"
                tvHistoryAmount.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            when (record.status) {
                TransactionRecord.STATUS_SENT -> {
                    tvHistoryStatus.text = "SENT"
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_status_success)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.secondary))
                    btnHistoryResend.text = "Sent"
                    btnHistoryResend.isEnabled = false
                }
                TransactionRecord.STATUS_FAILED -> {
                    tvHistoryStatus.text = "FAILED"
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_status_error)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.danger))
                    btnHistoryResend.text = "Retry"
                    btnHistoryResend.isEnabled = true
                }
                TransactionRecord.STATUS_DISCARDED -> {
                    tvHistoryStatus.text = "DISCARDED"
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_rounded_light)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    btnHistoryResend.text = "Send"
                    btnHistoryResend.isEnabled = true
                }
                else -> {
                    tvHistoryStatus.text = "PARSED"
                    tvHistoryStatus.setBackgroundResource(R.drawable.bg_rounded_light)
                    tvHistoryStatus.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    btnHistoryResend.text = "Send"
                    btnHistoryResend.isEnabled = true
                }
            }

            btnHistoryResend.setOnClickListener {
                onResend(record)
            }
        }
    }

    override fun getItemCount(): Int = records.size

    fun updateData(newRecords: List<TransactionRecord>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged()
    }
}
