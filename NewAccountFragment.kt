package com.fyndeverything.fynd.ui.account.newaccount

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.text.Html
import android.text.Spanned
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.fyndeverything.fynd.R
import com.fyndeverything.fynd.data.model.core.FyndError
import com.fyndeverything.fynd.data.model.response.FyndResponse
import com.fyndeverything.fynd.data.remote.NetworkManager
import com.fyndeverything.fynd.extension.hideKeyBoard
import com.fyndeverything.fynd.ui.account.verification.VerificationCodeActivity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.HintRequest
import com.google.android.gms.common.api.GoogleApiClient
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.szagurskii.patternedtextwatcher.PatternedTextWatcher
import dagger.android.support.DaggerFragment
import kotlinx.android.synthetic.main.fragment_new_account.*
import javax.inject.Inject

/**
 * Created by pedro varela on 5/12/17.
 *
 *
 * Request permission fragment
 */
class NewAccountFragment @Inject constructor() : DaggerFragment() {

    @Inject
    lateinit var phoneNumberUtil: PhoneNumberUtil

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var networkManager: NetworkManager

    @Inject
    lateinit var googleApiClient: GoogleApiClient

    @Inject
    lateinit var patternedTextWatcher: PatternedTextWatcher

    /**
     * Phone number verification dialog
     */
    private var phoneNumberVerificationDialog: AlertDialog? = null
    /**
     * SMS message dialog
     */
    private var smsMessageDialog: AlertDialog? = null
    /**
     *  ViewModel of this fragment
     */
    private var viewModel: NewAccountViewModel? = null


    /**
     * On activity result
     *
     * @param requestCode request code
     * @param resultCode  result code
     * @param data        Data
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == HINT) {

            if (resultCode == Activity.RESULT_OK) {

                data?.getParcelableExtra<Credential>(Credential.EXTRA_KEY)?.apply {

                    var phone = id

                    if (phone.contains("+1")) {
                        phone = phone.substring(2, phone.length)
                    }

                    text_input_edit_text_phone_number?.setText(phone)
                }


            } else if (resultCode == 1001) {
                text_input_edit_text_phone_number?.setText("")
            }
        }
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {

        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.let { ViewModelProviders.of(it, viewModelFactory).get(NewAccountViewModel::class.java) }

        viewModel?.newAccountResponse?.observe(this, Observer<FyndResponse> {

            if (it != null) {

                showSMSAlertDialog(false)

                if (it.errors.isNotEmpty()) {

                    val error: FyndError = it.errors[0]

                    text_input_layout_phone_number.error = activity?.let { error.getMessage(it) }

                } else {

                    startActivity(Intent(activity, VerificationCodeActivity::class.java))

                    activity?.finish()

                }
            }
        })

    }

    /**
     * Create the view of this fragment
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_new_account, container, false)
    }


    /**
     * Set actions after view is created
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        button_continue.setOnClickListener { v: View ->

            activity?.let {
                v.hideKeyBoard(it)
            }

            attemptSignUp()

        }

        button_select_phone_number.setOnClickListener { requestHint() }

        text_input_edit_text_phone_number.addTextChangedListener(patternedTextWatcher)

        constraint_layout.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus)
                this.activity?.let {
                    v.hideKeyBoard(it)
                }
        }
    }

    /**
     * Validates fields and attempt sign up
     */
    private fun attemptSignUp() {

        val phoneNumber = text_input_edit_text_phone_number?.text.toString()

        val isValidPhoneNumber = Patterns.PHONE.matcher(phoneNumber).matches()

        val isFullPhoneNumber = PhoneNumberUtils.normalizeNumber(phoneNumber).length == 12

        val isEmptyPhoneNumber = phoneNumber.isEmpty()

        val containsPlusSign = phoneNumber.contains("+")

        var focusView: View? = null

        if (isEmptyPhoneNumber) {
            text_input_layout_phone_number.error = getString(R.string.sign_up_error_field_required)
            focusView = text_input_edit_text_phone_number
        } else if (!isValidPhoneNumber || !isFullPhoneNumber || !containsPlusSign) {
            text_input_layout_phone_number.error = getString(R.string.sign_up_error_invalid_phone_number)
            focusView = text_input_edit_text_phone_number
        } else {
            text_input_layout_phone_number.error = ""
        }

        if (isValidPhoneNumber && isFullPhoneNumber && containsPlusSign && !isEmptyPhoneNumber) {
            showPhoneNumberVerificationDialog(phoneNumber)
        } else {
            focusView?.requestFocus()
        }

    }

    /**
     * Request phone number hint
     */
    private fun requestHint() {

        val hintRequest: HintRequest = HintRequest.Builder().setPhoneNumberIdentifierSupported(true).build()

        val pendingIntent: PendingIntent? = Auth.CredentialsApi.getHintPickerIntent(googleApiClient, hintRequest)

        activity?.startIntentSenderForResult(pendingIntent?.intentSender, HINT, null, 0, 0, 0)

    }

    /**
     * Show a dialog to last minute edit the phone number before send it to the server.
     */
    private fun showPhoneNumberVerificationDialog(phoneNumber: String) {

        val message: Spanned = when {

            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N -> {
                Html.fromHtml(resources.getString(R.string.new_account_verification_dialog_phone_number, phoneNumber), Html.FROM_HTML_MODE_LEGACY)
            }
            else -> {
                @Suppress("DEPRECATION")
                Html.fromHtml(resources.getString(R.string.new_account_verification_dialog_phone_number, phoneNumber))
            }
        }

        phoneNumberVerificationDialog = activity?.let {
            AlertDialog.Builder(it)
                    .setNegativeButton(getString(R.string.new_account_verification_dialog_edit_number)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(getString(android.R.string.ok)) { dialog, _ ->
                        dialog.dismiss()
                        attemptNewAccountRequest(phoneNumber)
                    }
                    .setIcon(ContextCompat.getDrawable(it, R.drawable.ic_text_white))
                    .setMessage(message)
                    .setTitle("")
                    .show()
        }
    }

    /**
     * Send the request to the server
     */
    private fun attemptNewAccountRequest(phoneNumber: String) {

        showSMSAlertDialog(true)

        if (networkManager.isOnline()) {
            viewModel?.getNewAccount(phoneNumber)
        } else {
            showSMSAlertDialog(false)
            Toast.makeText(activity, getString(R.string.internet_connection), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private fun showSMSAlertDialog(show: Boolean) {

        if (show) {
            smsMessageDialog = activity?.let {
                AlertDialog.Builder(it)
                        .setIcon(ContextCompat.getDrawable(it, R.drawable.ic_text_white))
                        .setMessage(getString(R.string.sign_up_sending_sms))
                        .setTitle("")
                        .show()
            }
        } else {
            smsMessageDialog?.run { if (isShowing) dismiss() }
        }
    }


    /**
     *
     */
    companion object {
        /**
         * TAG of this class
         */
        val TAG: String = NewAccountFragment::class.java.name
        /*
         *
         */
        const val HINT = 10
    }
}