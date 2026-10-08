package com.industri.smartpos

import java.io.IOException

// 1. Eksepsi barcode tidak terdaftar
class ProductBarcodeNotFoundException(val barcode: String) :
    Exception("Barang dengan barcode [$barcode] tidak terdaftar pada katalog sistem.")

// 2. Eksepsi timeout jaringan payment gateway
class PaymentGatewayTimeoutException(val gatewayName: String) :
    IOException("Layanan pembayaran $gatewayName tidak merespons dalam 15 detik.")

// 3. Eksepsi Otorisasi Limit Kasir > Rp 10.000.000 (Tugas Mandiri 2)
class CashierLimitExceededException(val maxLimit: Double = 10000000.0) :
    Exception("Total transaksi melebihi limit otorisasi kasir mandiri (Maksimal Rp %,.0f). Perlu otorisasi supervisor!".format(maxLimit))