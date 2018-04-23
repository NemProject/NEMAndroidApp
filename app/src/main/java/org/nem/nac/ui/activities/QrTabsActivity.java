package org.nem.nac.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;

import com.annimon.stream.Optional;

import org.nem.nac.R;
import org.nem.nac.application.AppSettings;
import org.nem.nac.datamodel.repositories.AccountRepository;
import org.nem.nac.models.account.Account;
import org.nem.nac.models.primitives.AddressValue;
import org.nem.nac.ui.adapters.ViewPagerAdapter;
import org.nem.nac.ui.fragments.CreateInvoiceFragment;
import org.nem.nac.ui.fragments.MyInfoFragment;
import org.nem.nac.ui.fragments.ScanFragment;
import org.nem.nac.ui.utils.Toaster;

import timber.log.Timber;

public final class QrTabsActivity extends NacBaseActivity {

	private static final String STATEVAL_PARC_ADDRESS = "stateval-address";
	private TabLayout _tabLayout;
	private ViewPager _viewPager;
	private final ScanFragment _scanFragment = ScanFragment.create(false);
	private ViewPagerAdapter _adapter;
	private AddressValue _address;

	@Override
	protected int getActivityTitle() {
		return R.string.title_activity_qr_tabs;
	}

	@Override
	protected int getLayoutId() {
		return R.layout.activity_qr_tabs;
	}

	@Override
	public void onBackPressed() {
		finish();
		startActivity(new Intent(this, DashboardActivity.class));
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		AddressValue saved = null;
		if (savedInstanceState != null) {
			saved = savedInstanceState.<AddressValue>getParcelable(STATEVAL_PARC_ADDRESS);
		}
		if (saved != null) {
			_address = saved;
		}
		else {
			final Optional<AddressValue> lastAddress = AppSettings.instance().readLastUsedAccAddress();
			if (lastAddress.isPresent()) {
				_address = lastAddress.get();
			}
			else {
				Timber.w("Last used address not present.");
				Toaster.instance().showGeneralError();
				return;
			}
		}
		//
		// Check if existing account
		final Optional<Account> account = new AccountRepository().find(_address);
		if (!account.isPresent()) {
			Timber.w("Local account not found: %s", _address);
			Toaster.instance().showGeneralError();
			return;
		}
		//
		_viewPager = findViewById(R.id.viewpager);
		//
		_adapter = new ViewPagerAdapter(getSupportFragmentManager());
		_adapter.addFragment(MyInfoFragment.create(account.get()), getString(R.string.title_fragment_my_info));
		_adapter.addFragment(CreateInvoiceFragment.create(account.get()), getString(R.string.title_fragment_create_invoice));
		_adapter.addFragment(_scanFragment, getString(R.string.title_fragment_scan));
		_viewPager.setAdapter(_adapter);
		_viewPager.setOffscreenPageLimit(2);
		//
		_tabLayout = (TabLayout)findViewById(R.id.tabs);
		_tabLayout.setupWithViewPager(_viewPager);
	}
}
