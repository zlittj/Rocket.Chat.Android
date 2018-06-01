package chat.rocket.android.chatrooms.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import chat.rocket.android.chatrooms.adapter.ItemHolder
import chat.rocket.android.chatrooms.adapter.RoomMapper
import chat.rocket.android.chatrooms.domain.FetchChatRoomsInteractor
import chat.rocket.android.chatrooms.infrastructure.ChatRoomsRepository
import chat.rocket.android.server.infraestructure.ConnectionManager
import chat.rocket.android.util.livedata.TransformedLiveData
import chat.rocket.core.internal.realtime.socket.model.State
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import me.henrytao.livedataktx.distinct
import me.henrytao.livedataktx.nonNull
import timber.log.Timber

class ChatRoomsViewModel(
    private val connectionManager: ConnectionManager,
    private val interactor: FetchChatRoomsInteractor,
    private val repository: ChatRoomsRepository,
    private val mapper: RoomMapper
) : ViewModel() {
    private val ordering: MutableLiveData<ChatRoomsRepository.Order> = MutableLiveData()
    private val runContext = newSingleThreadContext("chat-rooms-view-model")

    init {
        ordering.value = ChatRoomsRepository.Order.ACTIVITY
    }

    fun getChatRooms(): LiveData<List<ItemHolder<*>>> {
        return Transformations.switchMap(ordering) { order ->
            Timber.d("Querying rooms for order: $order")
            val grouped = order == ChatRoomsRepository.Order.GROUPED_ACTIVITY
                    || order == ChatRoomsRepository.Order.GROUPED_NAME
            val roomData = repository.getChatRooms(order).nonNull().distinct()
            TransformedLiveData(runContext, roomData) { rooms ->
                rooms?.let {
                    mapper.map(rooms, grouped)
                }
            }.nonNull()
        }
    }

    fun getStatus(): MutableLiveData<State> {
        return Transformations.map(connectionManager.statusLiveData.nonNull().distinct()) { state ->
            if (state is State.Connected) {
                // TODO - add a loading status...
                fetchRooms()
            }
            state
        }.nonNull()
    }

    private fun fetchRooms() {
        launch {
            interactor.refreshChatRooms()
        }
    }

    fun setOrdering(order: ChatRoomsRepository.Order) {
        ordering.value = order
    }
}
