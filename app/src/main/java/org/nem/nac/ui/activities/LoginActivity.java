package org.nem.nac.ui.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.annimon.stream.Optional;

import org.nem.nac.BuildConfig;
import org.nem.nac.FingerPrint.StartFingerprint;
import org.nem.nac.R;
import org.nem.nac.application.AppConstants;
import org.nem.nac.application.AppHost;
import org.nem.nac.application.AppSettings;
import org.nem.nac.common.async.AsyncResult;
import org.nem.nac.crypto.Mcrypto;
import org.nem.nac.datamodel.NacPersistenceRuntimeException;
import org.nem.nac.datamodel.repositories.AccountRepository;
import org.nem.nac.models.BinaryData;
import org.nem.nac.models.primitives.AddressValue;
import org.nem.nac.providers.EKeyProvider;
import org.nem.nac.servers.ServerFinder;
import org.nem.nac.tasks.CheckAppPasswordTask;
import org.nem.nac.ui.utils.InputErrorUtils;
import org.nem.nac.ui.utils.Toaster;

import timber.log.Timber;

public final class LoginActivity extends NacBaseActivity {

	public static final String EXTRA_BOOL_SIMPLY_FINISH = LoginActivity.class.getCanonicalName() + ".simply_finish";
	public static final String EXTRA_BOOL_EXIT_ATTEMPT  = LoginActivity.class.getCanonicalName() + ".exit_attempt";

	public static Intent getIntent(Context packageContext, final Bundle extras, boolean simplyFinish) {
		final Intent intent = new Intent(packageContext, LoginActivity.class);
		//	.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_BOOL_SIMPLY_FINISH, simplyFinish);
		if (extras != null) {
			intent.putExtras(extras);
		}
		return intent;
	}

	private ViewGroup _dialogLayout;
	private EditText _passwordInput;
	private boolean _simplyFinish = false;

	@Override
	protected boolean listenToKeyboardVisibility() {
		return true;
	}

	@Override
	protected int getActivityTitle() {
		return R.string.title_activity_login;
	}

	@Override
	protected int getLayoutId() {
		return R.layout.activity_login;
	}

	@Override
	public void onBackPressed() {
		if (Build.VERSION.SDK_INT >= 16) {
			finishAffinity();
		}
		else {
			Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.putExtra(EXTRA_BOOL_EXIT_ATTEMPT, true);
			startActivity(intent);
			finish();
		}
	}

	private View btnLogin;
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getExtras() != null) {
			if (getIntent().hasExtra(EXTRA_BOOL_EXIT_ATTEMPT)) {
				finish();
				return;
			}
			_simplyFinish = getIntent().getExtras().getBoolean(EXTRA_BOOL_SIMPLY_FINISH, false);
		}

		_dialogLayout = (ViewGroup)findViewById(R.id.layout_dialog);
		btnLogin = findViewById(R.id.btn_login);
		btnLogin.setOnClickListener(this::onLoginClick);
		_passwordInput = (EditText)findViewById(R.id.input_password);
		_passwordInput.addTextChangedListener(_clearErrorWatcher);
		// todo: remove this stub
		if (BuildConfig.DEBUG) {
			_passwordInput.setText("123456");
			_passwordInput.setSelectAllOnFocus(true);

		}
		btnLogin.setOnFocusChangeListener((v, hasFocus) -> {
			if (hasFocus) {
				AppHost.SoftKeyboard.forceHide(this);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!ServerFinder.instance().peekBest().isPresent()) {
			ServerFinder.instance().getBestAsync();
		}
		final Bundle extras = getIntent().getExtras();
		final Optional<BinaryData> eKey = EKeyProvider.instance().getKey();
		if (eKey.isPresent()) {
			Timber.d("Key present, checking...");
			_dialogLayout.setVisibility(View.GONE);
			showProgressDialog(R.string.progress_dialog_message_password_checking);
			getHandler().postDelayed(() -> {
				if (isNotDestroyed()) {
					if (extras != null && extras.containsKey(DashboardActivity.EXTRA_PARC_ACCOUNT_ADDRESS)) {
						final Intent intent = new Intent(this, DashboardActivity.class)
								.putExtras(extras);
						startActivity(intent);
					}
					else if (lastUsedAddress != null) {
						final Intent intent = new Intent(this, DashboardActivity.class);
						if (extras != null && !extras.isEmpty()) {
							intent.putExtras(extras);
						}
						startActivity(intent);
					}
					else {
						AccountListActivity.start(this);
					}
					finish();
				}
			}, 1000);
		}
		else {
			Timber.d("Key not present, waiting for password input");
			///  don' show finger print if pw not store
			String pwStoreIsEmpty= AppSettings.instance().getPassword();
			if (pwStoreIsEmpty!=null){
				checkStartFingerprint();
			}
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void setToolbarItems(@NonNull final Toolbar toolbar) {
		final View backIcon = toolbar.findViewById(R.id.toolbar_left_icon);
		backIcon.setVisibility(View.INVISIBLE);
		backIcon.setClickable(false);
	}

	private void onLoginClick(final View clicked) {
		Timber.d("Login btn click!");
		new CheckAppPasswordTask(this)
				.withCompleteCallback(this::onPasswordChecked)
				.execute(_passwordInput.getText().toString());
	}

	private void onPasswordChecked(final CheckAppPasswordTask task, final AsyncResult<Boolean> result) {
		final Optional<? extends Boolean> isValid = result.getResult();
		if (!isValid.isPresent()) {
			Toaster.instance().show(R.string.errormessage_error_occured);
			return;
		}
		if (isFinishing()) {
			return;
		}
		if (isValid.get()) {
			try {
				if (_simplyFinish) {
					finish();
					return;
				}
				if (new AccountRepository().any()) {
					final Bundle extras = getIntent().getExtras();
					if (extras != null && extras.containsKey(DashboardActivity.EXTRA_PARC_ACCOUNT_ADDRESS)) {
						final Intent intent = new Intent(this, DashboardActivity.class)
								.putExtras(extras);
						finish();
						startActivity(intent);
					}
					else {
						final AppSettings appSettings = AppSettings.instance();
						final Optional<AddressValue> primaryAddress = appSettings.getPrimaryAddress();
						if (primaryAddress.isPresent()) {
							final Intent intent = new Intent(this, DashboardActivity.class)
									.putExtra(DashboardActivity.EXTRA_PARC_ACCOUNT_ADDRESS, primaryAddress.get());
							finish();
							startActivity(intent);
							return;
						}
						final Optional<AddressValue> lastAddress = appSettings.readLastUsedAccAddress();
						if (lastAddress.isPresent()) {
							final Intent intent = new Intent(this, DashboardActivity.class)
									.putExtra(DashboardActivity.EXTRA_PARC_ACCOUNT_ADDRESS, lastAddress.get());
							finish();
							startActivity(intent);
						}
						else {
							AccountListActivity.start(this);
						}
					}
				}
				else {
					startActivity(new Intent(this, AddAccountActivity.class));
				}
			} catch (NacPersistenceRuntimeException e) {
				Toaster.instance().show(R.string.errormessage_error_occured, Toaster.Length.LONG);
			}
		}
		else if (_passwordInput != null) {
			InputErrorUtils.setErrorState(_passwordInput, R.string.errormessage_invalid_password);
		}
	}

	private final TextWatcher _clearErrorWatcher = new TextWatcher() {
		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {}

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {}

		@Override
		public void afterTextChanged(final Editable s) {
			InputErrorUtils.clearErrorState(_passwordInput);
		}
	};

	//////////  finger print
	private boolean forgotPWmode=false;
	private void checkStartFingerprint() {
		if (forgotPWmode){
			forgotPWmode=false;
			return;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			//Fingerprint API only available on from Android 6.0 (M)
			FingerprintManager fingerprintManager = (FingerprintManager) getApplicationContext().getSystemService(Context.FINGERPRINT_SERVICE);
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
				// TODO: Consider calling
				//    ActivityCompat#requestPermissions
				// here to request the missing permissions, and then overriding
				//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
				//                                          int[] grantResults)
				// to handle the case where the user grants the permission. See the documentation
				// for ActivityCompat#requestPermissions for more details.
				return;
			}
			if (!fingerprintManager.isHardwareDetected()) {
				// Device doesn't support fingerprint authentication
				Button fpButton = (Button) findViewById(R.id.btn_fingerprint);
				fpButton.setVisibility(View.GONE);
			} else if (!fingerprintManager.hasEnrolledFingerprints()) {
				// User hasn't enrolled any fingerprints to authenticate with
				Toast.makeText(this, R.string.login_reg_fingerprint, Toast.LENGTH_LONG).show();
			} else {
				StartFingerprint StartFP = new StartFingerprint(this);
				FingerprintPWThread(); // Everything is ready for fingerprint authentication
			}
		} else {
			// Device doesn't support fingerprint authentication
			Button fpButton = (Button) findViewById(R.id.btn_fingerprint);
			fpButton.setVisibility(View.GONE);
		}
	}

	public static Thread loginFingerprintPWThread;
	private void FingerprintPWThread(){
		loginFingerprintPWThread = new Thread() {
			@Override
			public void run() {
				String encPw= AppSettings.instance().getPassword();
				if (encPw==null)
						return;
				String pw= null;
				try {
					pw = new Mcrypto().decryptPassword(encPw, AppConstants.secKey);
				} catch (Exception e) {
					e.printStackTrace();
				}
				//Toaster.instance().show("Password: "+pw);
				if (BuildConfig.DEBUG) {
					_passwordInput.setText("");
				}
				_passwordInput.setText(pw);
				btnLogin.performClick();
			}
		};
	}
}
