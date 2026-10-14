package com.mealmacros.app.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Scans a product barcode with Google Play services' scanner (no camera permission needed).
 * [onError] is called when the scanner isn't available, e.g. on phones without Play services.
 */
fun scanBarcode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
    try {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
            .addOnFailureListener { e -> onError(e.message ?: "The barcode scanner isn't available on this phone.") }
    } catch (e: Exception) {
        onError(e.message ?: "The barcode scanner isn't available on this phone.")
    }
}
