package com.industri.smartpos

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PosActivity : AppCompatActivity() {

    private lateinit var etBarcode: EditText
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: CardView
    private lateinit var tvPosStatus: TextView
    private lateinit var tvPosDetails: TextView
    private lateinit var rgSimulation: RadioGroup

    // Counter percobaan ulang untuk Tugas Mandiri 1
    private var retryAttemptCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        etBarcode = findViewById(R.id.etBarcode)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosStatus = findViewById(R.id.tvPosStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)
        rgSimulation = findViewById(R.id.rgSimulation)

        btnProcessPayment.setOnClickListener {
            val barcode = etBarcode.text.toString().trim()
            if (barcode.isNotEmpty()) {
                eksekusiTransaksi(barcode)
            } else {
                Toast.makeText(this, "Barcode wajib diisi atau dipindai", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun eksekusiTransaksi(barcode: String) {
        pbPosLoading.visibility = View.VISIBLE
        cardPosResult.visibility = View.GONE
        btnProcessPayment.isEnabled = false

        val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception, "FATAL COROUTINE ERROR TERTANGKAP: %s", exception.message)
            runOnUiThread {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true
                tampilkanSnackbarError("Kesalahan sistem tak terduga: ${exception.localizedMessage}", canRetry = false)
            }
        }

        lifecycleScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            Timber.d("Memulai alur pembayaran untuk Barcode: %s", barcode)

            val hasil = runCatching {
                delay(1200) // Simulasi latensi jaringan

                when (rgSimulation.checkedRadioButtonId) {
                    R.id.rbBarcodeError -> {
                        throw ProductBarcodeNotFoundException(barcode)
                    }
                    R.id.rbNetworkTimeout -> {
                        throw PaymentGatewayTimeoutException("QRIS Bank Settlement")
                    }
                    R.id.rbLimitExceeded -> {
                        throw CashierLimitExceededException(10000000.0)
                    }
                    else -> {
                        PosTransaction(
                            transactionId = "TRX-2026-9901",
                            barcode = barcode,
                            productName = "Susu UHT Full Cream 1 Liter",
                            totalAmount = 21500.0,
                            status = "SETTLED_SUCCESS"
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                pbPosLoading.visibility = View.GONE
                btnProcessPayment.isEnabled = true

                hasil.onSuccess { trx ->
                    retryAttemptCount = 0 // Reset counter saat sukses
                    Timber.i("Transaksi kasir berhasil dicatat: %s", trx.transactionId)

                    cardPosResult.visibility = View.VISIBLE
                    tvPosStatus.text = "TRANSAKSI BERHASIL (LUNAS)"
                    tvPosDetails.text = """
                        ID Transaksi : ${trx.transactionId}
                        Produk       : ${trx.productName}
                        Total Bayar  : Rp %,.2f
                        Waktu        : Baru Saja
                    """.trimIndent().format(trx.totalAmount)
                }.onFailure { err ->
                    Timber.w("Transaksi kasir ditolak / gagal: %s", err.message)

                    when (err) {
                        is ProductBarcodeNotFoundException -> {
                            tampilkanSnackbarError(err.message ?: "Barcode tidak ditemukan", canRetry = false)
                        }
                        is PaymentGatewayTimeoutException -> {
                            // TUGAS MANDIRI 1: Max Retry Threshold 3x
                            retryAttemptCount++
                            if (retryAttemptCount >= 3) {
                                tampilkanDialogFallbackTunai()
                            } else {
                                tampilkanSnackbarError("${err.message} (Coba ke-$retryAttemptCount/3)", canRetry = true)
                            }
                        }
                        is CashierLimitExceededException -> {
                            // TUGAS MANDIRI 2: Otorisasi PIN Supervisor Kasir
                            tampilkanDialogPinSupervisor(err.message ?: "Limit Terlampaui")
                        }
                        else -> {
                            tampilkanSnackbarError("Kegagalan operasional: ${err.message}", canRetry = true)
                        }
                    }
                }
            }
        }
    }

    private fun tampilkanSnackbarError(pesan: String, canRetry: Boolean) {
        val root = findViewById<View>(R.id.coordinatorLayout)
        val snackbar = Snackbar.make(root, pesan, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(Color.parseColor("#991B1B")) // Merah
        snackbar.setTextColor(Color.WHITE)

        if (canRetry) {
            snackbar.setAction("COBA LAGI") {
                val barcode = etBarcode.text.toString().trim()
                if (barcode.isNotEmpty()) eksekusiTransaksi(barcode)
            }
            snackbar.setActionTextColor(Color.parseColor("#FEF08A"))
        }
        snackbar.show()
    }

    // TUGAS MANDIRI 1: Dialog Rekomendasi Bayar Tunai setelah 3x gagal
    private fun tampilkanDialogFallbackTunai() {
        AlertDialog.Builder(this)
            .setTitle("Koneksi Perbankan Terputus")
            .setMessage("Percobaan ulang telah dilakukan 3 kali. Disarankan mengalihkan pelanggan ke 'Pembayaran Tunai Manual'.")
            .setPositiveButton("Beralih ke Tunai") { dialog, _ ->
                retryAttemptCount = 0
                Toast.makeText(this, "Mode Pembayaran Tunai Manual Diaktifkan", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                retryAttemptCount = 0
                dialog.dismiss()
            }
            .show()
    }

    // TUGAS MANDIRI 2: Dialog Otorisasi Supervisor
    private fun tampilkanDialogPinSupervisor(pesanError: String) {
        val inputPin = EditText(this)
        inputPin.hint = "Masukkan PIN Supervisor (Cth: 1234)"
        inputPin.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Otorisasi Supervisor Dibutuhkan")
            .setMessage(pesanError)
            .setView(inputPin)
            .setPositiveButton("Verifikasi") { dialog, _ ->
                val pin = inputPin.text.toString()
                if (pin == "1234") {
                    Toast.makeText(this, "Otorisasi Berhasil! Transaksi disetujui Supervisor.", Toast.LENGTH_LONG).show()
                    cardPosResult.visibility = View.VISIBLE
                    tvPosStatus.text = "TRANSAKSI DI-OTORISASI (LUNAS)"
                    tvPosDetails.text = "Limit Disetujui oleh Supervisor\nTotal Transaksi: Rp 12.500.000"
                } else {
                    Toast.makeText(this, "PIN Supervisor Salah! Transaksi Dibatalkan.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}