package com.example.canteen.data.model

enum class CancelReason(val label: String) {
    SOLD_OUT("Hết món"),
    OVERLOAD("Quá tải"),
    NO_SHOW("Không đến lấy"),
    CHANGED_MIND("Học sinh đổi ý"),   // chỉ dùng khi học sinh tự hủy
    OTHER("Khác")
}

