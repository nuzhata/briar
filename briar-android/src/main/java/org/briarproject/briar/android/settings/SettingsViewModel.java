package org.briarproject.briar.android.settings;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;
import android.widget.Toast;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.logging.PersistentLogManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.briar.R;
import org.briarproject.briar.android.attachment.UnsupportedMimeTypeException;
import org.briarproject.briar.android.attachment.media.ImageCompressor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.api.avatar.AvatarManager;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.AnyThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Arrays.asList;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.AndroidUtils.getPersistentLogDir;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.android.settings.SecurityFragment.PREF_SCREEN_LOCK;
import static org.briarproject.briar.android.settings.SecurityFragment.PREF_SCREEN_LOCK_TIMEOUT;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class SettingsViewModel extends DbViewModel implements EventListener {

	private final static Logger LOG =
			getLogger(SettingsViewModel.class.getName());

	static final String BT_NAMESPACE =
			BluetoothConstants.ID.getString();
	static final String WIFI_NAMESPACE = LanTcpConstants.ID.getString();
	static final String TOR_NAMESPACE = TorConstants.ID.getString();

	private final SettingsManager settingsManager;
	private final IdentityManager identityManager;
	private final EventBus eventBus;
	private final AvatarManager avatarManager;
	private final AuthorManager authorManager;
	private final ImageCompressor imageCompressor;
	private final Executor ioExecutor;
	private final FeatureFlags featureFlags;
	private final PersistentLogManager logManager;

	final SettingsStore settingsStore;
	final TorSummaryProvider torSummaryProvider;
	final ConnectionsManager connectionsManager;
	final NotificationsManager notificationsManager;

	private volatile Settings settings;

	private final MutableLiveData<OwnIdentityInfo> ownIdentityInfo =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> screenLockEnabled =
			new MutableLiveData<>();
	private final MutableLiveData<String> screenLockTimeout =
			new MutableLiveData<>();

	@Inject
	SettingsViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			SettingsManager settingsManager,
			IdentityManager identityManager,
			EventBus eventBus,
			AvatarManager avatarManager,
			AuthorManager authorManager,
			ImageCompressor imageCompressor,
			LocationUtils locationUtils,
			CircumventionProvider circumventionProvider,
			@IoExecutor Executor ioExecutor,
			FeatureFlags featureFlags,
			PersistentLogManager logManager) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.settingsManager = settingsManager;
		this.identityManager = identityManager;
		this.eventBus = eventBus;
		this.imageCompressor = imageCompressor;
		this.avatarManager = avatarManager;
		this.authorManager = authorManager;
		this.ioExecutor = ioExecutor;
		this.featureFlags = featureFlags;
		this.logManager = logManager;
		settingsStore = new SettingsStore(settingsManager, dbExecutor,
				SETTINGS_NAMESPACE);
		torSummaryProvider = new TorSummaryProvider(getApplication(),
				locationUtils, circumventionProvider);
		connectionsManager =
				new ConnectionsManager(settingsManager, dbExecutor);
		notificationsManager = new NotificationsManager(getApplication(),
				settingsManager, dbExecutor);

		eventBus.addListener(this);
		loadSettings();
		if (shouldEnableProfilePictures()) loadOwnIdentityInfo();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	private void loadSettings() {
		runOnDbThread(() -> {
			try {
				long start = now();
				settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
				updateSettings(settings);
				connectionsManager.updateBtSetting(
						settingsManager.getSettings(BT_NAMESPACE));
				connectionsManager.updateWifiSettings(
						settingsManager.getSettings(WIFI_NAMESPACE));
				connectionsManager.updateTorSettings(
						settingsManager.getSettings(TOR_NAMESPACE));
				logDuration(LOG, "Loading settings", start);
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	boolean shouldEnableProfilePictures() {
		return featureFlags.shouldEnableProfilePictures();
	}

	private void loadOwnIdentityInfo() {
		runOnDbThread(() -> {
			try {
				LocalAuthor localAuthor = identityManager.getLocalAuthor();
				AuthorInfo authorInfo = authorManager.getMyAuthorInfo();
				ownIdentityInfo.postValue(
						new OwnIdentityInfo(localAuthor, authorInfo));
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			String namespace = s.getNamespace();
			if (namespace.equals(SETTINGS_NAMESPACE)) {
				LOG.info("Settings updated");
				settings = s.getSettings();
				updateSettings(settings);
			} else if (namespace.equals(BT_NAMESPACE)) {
				LOG.info("Bluetooth settings updated");
				connectionsManager.updateBtSetting(s.getSettings());
			} else if (namespace.equals(WIFI_NAMESPACE)) {
				LOG.info("Wifi settings updated");
				connectionsManager.updateWifiSettings(s.getSettings());
			} else if (namespace.equals(TOR_NAMESPACE)) {
				LOG.info("Tor settings updated");
				connectionsManager.updateTorSettings(s.getSettings());
			}
		}
	}

	@AnyThread
	private void updateSettings(Settings settings) {
		screenLockEnabled.postValue(settings.getBoolean(PREF_SCREEN_LOCK,
				false));
		int defaultTimeout = Integer.parseInt(getApplication()
				.getString(R.string.pref_lock_timeout_value_default));
		screenLockTimeout.postValue(String.valueOf(
				settings.getInt(PREF_SCREEN_LOCK_TIMEOUT, defaultTimeout)
		));
		notificationsManager.updateSettings(settings);
	}

	void setAvatar(Uri uri) {
		ioExecutor.execute(() -> {
			try {
				trySetAvatar(uri);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onSetAvatarFailed();
			}
		});
	}

	private void trySetAvatar(Uri uri) throws IOException {
		ContentResolver contentResolver =
				getApplication().getContentResolver();
		String contentType = contentResolver.getType(uri);
		if (contentType == null) throw new IOException("null content type");
		if (!asList(getSupportedImageContentTypes()).contains(contentType)) {
			throw new UnsupportedMimeTypeException(contentType, uri);
		}
		InputStream is = contentResolver.openInputStream(uri);
		if (is == null) throw new IOException(
				"ContentResolver returned null when opening InputStream");
		InputStream compressed = imageCompressor.compressImage(is, contentType);

		runOnDbThread(() -> {
			try {
				avatarManager.addAvatar(ImageCompressor.MIME_TYPE, compressed);
				loadOwnIdentityInfo();
			} catch (IOException | DbException e) {
				logException(LOG, WARNING, e);
				onSetAvatarFailed();
			}
		});
	}

	@AnyThread
	private void onSetAvatarFailed() {
		androidExecutor.runOnUiThread(() -> Toast.makeText(getApplication(),
				R.string.change_profile_picture_failed_message, LENGTH_LONG)
				.show());
	}

	LiveData<OwnIdentityInfo> getOwnIdentityInfo() {
		return ownIdentityInfo;
	}

	LiveData<Boolean> getScreenLockEnabled() {
		return screenLockEnabled;
	}

	LiveData<String> getScreenLockTimeout() {
		return screenLockTimeout;
	}

	void exportPersistentLog(boolean old, Uri uri) {
		// We can use untranslated strings here, as this method is only called
		// in debug builds
		ioExecutor.execute(() -> {
			Application app = getApplication();
			try {
				OutputStream os =
						app.getContentResolver().openOutputStream(uri);
				if (os == null) throw new IOException();
				File logDir = getPersistentLogDir(app);
				Scanner scanner = logManager.getPersistentLog(logDir, old);
				if (!scanner.hasNextLine()) {
					scanner.close();
					androidExecutor.runOnUiThread(() ->
							Toast.makeText(app, "Log is empty",
									LENGTH_LONG).show());
					return;
				}
				PrintWriter w = new PrintWriter(os);
				while (scanner.hasNextLine()) w.println(scanner.nextLine());
				w.flush();
				w.close();
				scanner.close();
				androidExecutor.runOnUiThread(() ->
						Toast.makeText(app, "Log exported",
								LENGTH_LONG).show());
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				androidExecutor.runOnUiThread(() ->
						Toast.makeText(app, "Failed to export log",
								LENGTH_LONG).show());
			}
		});
	}
}
