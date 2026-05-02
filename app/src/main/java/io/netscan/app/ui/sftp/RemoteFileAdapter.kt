package io.netscan.app.ui.sftp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.netscan.app.data.RemoteFileItem
import io.netscan.app.databinding.ItemRemoteFileBinding

class RemoteFileAdapter(
    private val onClick: (RemoteFileItem) -> Unit
) : ListAdapter<RemoteFileItem, RemoteFileAdapter.RemoteFileViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemoteFileViewHolder {
        val binding = ItemRemoteFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RemoteFileViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: RemoteFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RemoteFileViewHolder(
        private val binding: ItemRemoteFileBinding,
        private val onClick: (RemoteFileItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RemoteFileItem) {
            binding.fileName.text = item.name
            binding.fileMeta.text = if (item.isDirectory) "Directory" else "${item.sizeBytes} bytes"
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RemoteFileItem>() {
        override fun areItemsTheSame(oldItem: RemoteFileItem, newItem: RemoteFileItem): Boolean = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: RemoteFileItem, newItem: RemoteFileItem): Boolean = oldItem == newItem
    }
}
