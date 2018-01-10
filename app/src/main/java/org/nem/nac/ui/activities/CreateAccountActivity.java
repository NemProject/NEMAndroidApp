package org.nem.nac.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.nem.core.crypto.KeyPair;
import org.nem.nac.R;
import org.nem.nac.common.async.AsyncResult;
import org.nem.nac.models.NacPrivateKey;
import org.nem.nac.models.account.Account;
import org.nem.nac.providers.AddressInfoProvider;
import org.nem.nac.tasks.AddAccountTask;
import org.nem.nac.ui.controls.PrivateKeyInput;
import org.nem.nac.ui.utils.Toaster;

import java.util.NoSuchElementException;

import timber.log.Timber;

public final class CreateAccountActivity extends NacBaseActivity {

	public static final String EXTRA_BOOL_IMPORT_PRIVATE_KEY = CreateAccountActivity.class.getCanonicalName() + "extra-import-priv-key";

	private boolean _importPrivKey = false;
	private EditText        _inputAccName;
	private PrivateKeyInput _inputPrivateKey;
	private Button          _createAccountConfirm;

	@Override
	protected int getActivityTitle() {
		return R.string.title_activity_create_account;
	}

	@Override
	protected int getLayoutId() {
		return R.layout.activity_create_account;
	}

	@Override
	public void onBackPressed() {
		startActivity(new Intent(this, AddAccountActivity.class));
		finish();
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		_importPrivKey = getIntent().getBooleanExtra(EXTRA_BOOL_IMPORT_PRIVATE_KEY, false);
		setTitle(_importPrivKey ? R.string.title_activity_import_key : R.string.title_activity_create_account);

		_inputAccName = (EditText)findViewById(R.id.input_account_name);
		_inputAccName.addTextChangedListener(textWatcher);
		_inputPrivateKey = ((PrivateKeyInput)findViewById(R.id.input_private_key));
		_inputPrivateKey.addTextChangedListener(textWatcher);
		_createAccountConfirm = (Button)findViewById(R.id.btn_create_account_confirm);
		_createAccountConfirm.setOnClickListener(this::onConfirmAccount);
		if (_importPrivKey) {
			final TextView title = (TextView)findViewById(R.id.toolbar_title);
			title.setText(R.string.title_activity_import_key);
		}
		else {
			_inputPrivateKey.setVisibility(View.GONE);
			_inputAccName.setBackgroundColor(Color.TRANSPARENT);
		}
	}

	private void onConfirmAccount(final View clicked) {
		_createAccountConfirm.setClickable(false);
		if (_importPrivKey && !_inputPrivateKey.validate()) {
			return;
		}
		final String name = _inputAccName.getText().toString();
		if (name.isEmpty()) {
			_inputAccName.setError(getString(R.string.errormessage_account_name_empty));
			_createAccountConfirm.setClickable(true);
			return;
		}

		try {
			final NacPrivateKey privateKey = _importPrivKey
					? _inputPrivateKey.getPrivateKey().get()
					: new NacPrivateKey(new KeyPair().getPrivateKey().getRaw().toByteArray());
			new AddAccountTask(this, name)
					.withCompleteCallback(this::onAccountCreated)
					.execute(privateKey);
		} catch (NoSuchElementException e) {
			Timber.e(e, "Failed to get private key");
			Toaster.instance().show(R.string.errormessage_error_occured);
			_createAccountConfirm.setClickable(true);
		}
	}

	private void onAccountCreated(final AddAccountTask task, final AsyncResult<Account> result) {
		if (!result.getResult().isPresent()) {
			return;
		}
		AddressInfoProvider.instance().invalidateLocal();
		AccountListActivity.start(this);
	}

		public TextWatcher textWatcher = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {

		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {

		}

		@Override
		public void afterTextChanged(Editable s) {
			//Resetting the button state when the user change the text, otherwise the button remain unclickable
			_createAccountConfirm.setClickable(true);
		}
	};

}
