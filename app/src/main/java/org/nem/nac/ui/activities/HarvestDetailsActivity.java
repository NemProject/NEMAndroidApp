package org.nem.nac.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.annimon.stream.Optional;

import org.nem.nac.R;
import org.nem.nac.application.AppConstants;
import org.nem.nac.application.AppSettings;
import org.nem.nac.common.async.AsyncResult;
import org.nem.nac.common.utils.DateUtils;
import org.nem.nac.models.api.HarvestInfoApiDto;
import org.nem.nac.models.api.HarvestingInfoArrayApiDto;
import org.nem.nac.models.api.account.AccountMetaDataApiDto;
import org.nem.nac.models.api.account.AccountMetaDataPairApiDto;
import org.nem.nac.models.primitives.AddressValue;
import org.nem.nac.tasks.GetAccountInfoAsyncTask;
import org.nem.nac.tasks.GetHarvestInfoDataAsyncTask;

import timber.log.Timber;

public final class HarvestDetailsActivity extends NacBaseActivity {

	private TextView     _poiLabel;
	private TextView     _balanceLabel;
	private TextView     _vestedBalanceLabel;
	private TextView     _delegatedHarvestingStatusLabel;
	private TextView     _harvestedBlocksLabel;
	private LinearLayout _blocksList;
	private AddressValue _address;

	@Override
	protected int getActivityTitle() {
		return R.string.title_activity_harvest_details;
	}

	@Override
	protected int getLayoutId() {
		return R.layout.activity_harvest_details;
	}

	@Override
	public void onBackPressed() {
		finish();
		startActivity(new Intent(this, MoreActivity.class));
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//
		final Optional<AddressValue> lastAddress = AppSettings.instance().readLastUsedAccAddress();
		if (lastAddress.isPresent()) {
			_address = lastAddress.get();
		}
		//
		_poiLabel = (TextView)findViewById(R.id.textview_poi);
		_balanceLabel = (TextView)findViewById(R.id.textview_balance);
		_vestedBalanceLabel = (TextView)findViewById(R.id.textview_vested_balance);
		_delegatedHarvestingStatusLabel = (TextView)findViewById(R.id.textview_delegated_harvesting);
		_blocksList = (LinearLayout)findViewById(R.id.linear_list_blocks);
		_harvestedBlocksLabel = (TextView)findViewById(R.id.label_harvested_blocks);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (_address == null) {
			Timber.e("Last used address not present.");
			finish();
			AccountListActivity.start(this);
			return;
		}
		new GetAccountInfoAsyncTask(this, _address)
			.withCompleteCallback(this::onAccountInfo)
			.execute();
	}

	private void onAccountInfo(final GetAccountInfoAsyncTask task, final AsyncResult<AccountMetaDataPairApiDto> result) {
		if (!result.getResult().isPresent()) {
			Timber.e("Failed to get account info");
			return;
		}

		new GetHarvestInfoDataAsyncTask(this, _address)
			.withCompleteCallback(this::onHarvestInfo)
			.execute();

		final AccountMetaDataPairApiDto accountInfo = result.getResult().get();
		_poiLabel.setText(getString(R.string.label_poi, accountInfo.account.importance * 10000.0f));

		final String balanceStr = getString(R.string.label_balance, accountInfo.account.balance.toFractionalString());
		final SpannableStringBuilder balanceSb = new SpannableStringBuilder(balanceStr);
		final ForegroundColorSpan balanceColorSpan = new ForegroundColorSpan(getResources().getColor(R.color.official_green));
		balanceSb.setSpan(balanceColorSpan, balanceStr.indexOf(":") + 1, balanceSb.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
		_balanceLabel.setText(balanceSb);

		_vestedBalanceLabel.setText(getString(R.string.label_vested_balance, accountInfo.account.vestedBalance.toFractionalString()));

		final String statusStr = getString(accountInfo.meta.remoteStatus == AccountMetaDataApiDto.RemoteHarvestingStatus.ACTIVE
				? R.string.delegated_harvesting_unlocked
				: R.string.delegated_harvesting_locked);
		final String delegatedStr = getString(R.string.label_delegated_harvesting, statusStr);
		final SpannableStringBuilder delegatedSb = new SpannableStringBuilder(delegatedStr);
		final ForegroundColorSpan delegatedColorSpan = new ForegroundColorSpan(getResources().getColor(R.color.official_green));
		delegatedSb.setSpan(delegatedColorSpan, delegatedStr.indexOf(":") + 1, delegatedSb.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
		_delegatedHarvestingStatusLabel.setText(delegatedSb);
	}

	private void onHarvestInfo(final GetHarvestInfoDataAsyncTask task, final AsyncResult<HarvestingInfoArrayApiDto> result) {
		if (!result.getResult().isPresent()) {
			Timber.e("Failed to get harvest info");
			return;
		}

		if (isFinishing()) {
			return;
		}

		final HarvestInfoApiDto[] harvestInfos = result.getResult().get().data;
		if (harvestInfos.length == 0) {
			_harvestedBlocksLabel.setText(R.string.label_last_harvested_blocks_no_blocks);
		}
		else if (harvestInfos.length == 1) {
			_harvestedBlocksLabel.setText(getString(R.string.label_last_harvested_block));
		}
		else if (harvestInfos.length > 1) {
			_harvestedBlocksLabel.setText(getString(R.string.label_last_harvested_blocks, harvestInfos.length));
		}
		_blocksList.removeAllViews();
		final LayoutInflater inflater = LayoutInflater.from(this);

		for (HarvestInfoApiDto harvestInfo : harvestInfos) {
			final View blockView = inflater.inflate(R.layout.list_item_block_info, _blocksList, false);
			final TextView idLabel = (TextView)blockView.findViewById(R.id.label_block_id);
			final TextView timestampLabel = (TextView)blockView.findViewById(R.id.label_block_timestamp);
			final TextView feeLabel = (TextView)blockView.findViewById(R.id.label_block_fee);
			idLabel.setText(getString(R.string.label_block_id, harvestInfo.height));
			timestampLabel.setText(DateUtils.formatWithShortMonth(AppConstants.NEMESIS_BLOCK_TIMESTAMP.add(harvestInfo.timeStamp).toDate()));
			feeLabel.setText(getString(R.string.label_block_fee, harvestInfo.totalFee.toFractionalString()));
			_blocksList.addView(blockView);
		}
	}
}
