package com.pocrede.rede

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import rede.smartrede.commons.callback.IPrinterCallback
import rede.smartrede.commons.contract.IConnectorPrinter
import rede.smartrede.sdk.FlexTipoPagamento
import rede.smartrede.sdk.PaymentStatus
import rede.smartrede.sdk.RedePaymentValidationError
import rede.smartrede.sdk.RedePayments
import rede.smartrede.sdk.api.IRedeSdk
import java.util.logging.Logger


class RedeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext),
    ActivityEventListener {

    private val PAYMENT_REQUEST_CODE = 1001
    private val REVERSAL_REQUEST_CODE = 1002
    private val REPRINT_REQUEST_CODE = 1003
    private var paymentPromise: Promise? = null
    private var redePayments: RedePayments? = null
    private var redePrints: IConnectorPrinter? = null

    init {
        if (redePayments == null) {
            val redeSdk = IRedeSdk.newInstance(reactContext)
            val terminalFunctions = redeSdk.getTerminalFunctions()
            redePayments = redeSdk.getRedePayments(reactContext)
            redePrints = terminalFunctions.getConnectorPrinter()
            reactContext.addActivityEventListener(this)
        }
    }

    override fun getName() = "RedeModule"

    override fun getConstants(): Map<String, Any> {
        val constants: MutableMap<String, Any> = HashMap()
        constants["PIX"] = FlexTipoPagamento.PIX.toString()
        constants["CREDITO_A_VISTA"] = FlexTipoPagamento.CREDITO_A_VISTA.toString()
        constants["CREDITO_PARCELADO"] = FlexTipoPagamento.CREDITO_PARCELADO.toString()
        constants["CREDITO_PARCELADO_EMISSOR"] =  FlexTipoPagamento.CREDITO_PARCELADO_EMISSOR.toString()
        constants["DEBITO"] = FlexTipoPagamento.DEBITO.toString()
        constants["VOUCHER"] = FlexTipoPagamento.VOUCHER.toString()
        constants["FAILED"] = PaymentStatus.FAILED.toString()
        constants["DECLINED"] = PaymentStatus.DECLINED.toString()
        constants["FAILED"] = PaymentStatus.FAILED.toString()
        return constants
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun show(title: String) {
        val context: Context = reactApplicationContext
        Toast.makeText(context, title, Toast.LENGTH_LONG).show();
    }

    @ReactMethod()
    fun reversal(promise: Promise) {
        try {
            val currentActivity = currentActivity
            val reversal = redePayments!!.intentForReversal()
            currentActivity!!.startActivityForResult(reversal, REVERSAL_REQUEST_CODE)
            paymentPromise = promise
        } catch (e: ActivityNotFoundException) {
            promise.reject("error", e.message)
        }
    }

    @ReactMethod()
    fun reprint(promise: Promise) {
        try {
            val reprint = redePayments!!.intentForReprint()
            this.currentActivity?.startActivityForResult(reprint, REPRINT_REQUEST_CODE);
            paymentPromise = promise;
        }catch (e: ActivityNotFoundException) {
            e.message?.let { Logger.getLogger(it) }
            promise.reject("error", e.message)
        }
    }

    @ReactMethod
    fun payment(type: String?, value: Int, installments: Int, promise: Promise) {
        try {
            val paymentType = FlexTipoPagamento.valueOf(type!!)
            val collectPaymentIntent: Intent =
                redePayments?.intentForPaymentBuilder(paymentType, value.toLong())
                    ?.setInstallments(installments)?.build()
                    ?: throw RedePaymentValidationError("Invalid payment type")
            currentActivity?.startActivityForResult(collectPaymentIntent, PAYMENT_REQUEST_CODE);
            paymentPromise = promise;
        } catch (e: ActivityNotFoundException) {
            e.message?.let { Logger.getLogger(it) }
            promise.reject("error", e.message)
        } catch (e: RedePaymentValidationError) {
            e.message?.let { Logger.getLogger(it) }
            promise.reject("error", e.message)
        }
    }

    private fun postToWorkerThread(command: Runnable) {
        Thread(command).start()
    }

    private val callback: IPrinterCallback = object : IPrinterCallback {
        override fun onCompleted() {
            Toast.makeText(reactApplicationContext, "Impressão realizada com sucesso", Toast.LENGTH_LONG).show();
        }

        override fun onError(errorMessage: String) {
            Toast.makeText(reactApplicationContext, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    @ReactMethod()
    fun print(imgBase64: String, promise: Promise) {
        postToWorkerThread(Runnable {
            try {
                val bmp: Bitmap
                val imageByte: ByteArray = Base64.decode(imgBase64, Base64.DEFAULT);
                bmp = BitmapFactory.decodeByteArray(imageByte, 0, imageByte.size);
                redePrints?.setPrinterCallback(callback)
                redePrints?.printBitmap(bmp)
            } catch (e: Exception) {
                e.message?.let { Logger.getLogger(it) }
                promise.reject("error", e.message)
            }
        })
    }

    private fun RedePaymentValidationError(s: String): RedePaymentValidationError {
        return RedePaymentValidationError(s)
    }

    override fun onActivityResult(
        activity: Activity?,
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ) {
        if (requestCode == PAYMENT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val payment = intent?.let { RedePayments.getPaymentFromIntent(it) }
                when (payment?.status) {
                    PaymentStatus.AUTHORIZED -> {
                        val receipt = payment.receipt
                        val map = Arguments.createMap()
                        map.putString("retCode", payment.receipt.nsu)
                        map.putString("transactionCode", payment.receipt.nsu)
                        map.putString("transactionId", payment.receipt.nsu)
                        map.putString("message", payment.receipt.nsu)
                        map.putString("nsu", payment.receipt.nsu)
                        paymentPromise?.resolve(map)
                    }
                    PaymentStatus.FAILED -> {
                        paymentPromise?.reject("error", "Payment failed")
                    }
                    PaymentStatus.DECLINED -> {
                        paymentPromise?.reject("error", "Payment declined")
                    }
                    PaymentStatus.COMPLETED -> TODO()
                    PaymentStatus.VOIDED -> TODO()
                    PaymentStatus.REFUNDED -> TODO()
                    PaymentStatus.CANCELED -> TODO()
                    PaymentStatus.HOLD -> TODO()
                    null ->  paymentPromise?.reject("error", "error")
                }
            } else {
                paymentPromise?.reject("error", "Pagamento Cancelado pelo operador")
            }
        } else if (requestCode == REVERSAL_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (intent != null) {
                    val reversal = RedePayments.getPaymentFromIntent(intent)
                    if (reversal != null) {
                        if (reversal.status == PaymentStatus.AUTHORIZED) {
                            paymentPromise?.resolve("Autorizado")
                        } else if (reversal.status == PaymentStatus.DECLINED)
                            paymentPromise?.reject(null, "Reembolso Recusado");
                        } else {
                            paymentPromise?.reject(null, "Reembolso Falhou");
                    }
                } else {
                    paymentPromise?.reject("error", "Reembolso Cancelado pelo operador")
                }
            }
            paymentPromise?.reject("error", "Payment failed")
        } else if (requestCode == REPRINT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                paymentPromise?.resolve("Reimpressão realizada com sucesso")
            } else {
                paymentPromise?.reject("error", "Reimpressão cancelada pelo operador")
            }
        }
    }

    override fun onNewIntent(activity: Intent?) {
        TODO("Not yet implemented")
    }

}