package io.nekohasekai.sagernet.ui

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.security.KeyChain
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import moe.matsuri.nb4a.ui.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference

    private lateinit var globalCustomConfig: EditConfigPreference


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    private val reloadListener = Preference.OnPreferenceChangeListener { _, _ ->
        needReload()
        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)

        val mixedPort = findPreference<EditTextPreference>(Key.MIXED_PORT)!!
        val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
        val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
        val appendHttpProxy = findPreference<SwitchPreference>(Key.APPEND_HTTP_PROXY)!!

        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!
        val ipv6Mode = findPreference<Preference>(Key.IPV6_MODE)!!
        val trafficSniffing = findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!

        val bypassLan = findPreference<SwitchPreference>(Key.BYPASS_LAN)!!
        val bypassLanInCore = findPreference<SwitchPreference>(Key.BYPASS_LAN_IN_CORE)!!

        val killSwitchSettings = findPreference<Preference>("killSwitchSettings")!!
        killSwitchSettings.setOnPreferenceClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.kill_switch_open_failed, Toast.LENGTH_SHORT).show()
            }
            true
        }

        val installCaCert = findPreference<Preference>("installCaCert")!!
        installCaCert.setOnPreferenceClickListener {
            handleInstallCaCertClick()
            true
        }

        val secureBrowsing = findPreference<Preference>("secureBrowsing")!!
        secureBrowsing.setOnPreferenceClickListener {
            snackbar(R.string.secure_browsing_updating).show()
            runOnDefaultDispatcher {
                val count = io.nekohasekai.sagernet.bg.SecureBrowsing.updateBlocklist()
                onMainDispatcher {
                    if (count != null) {
                        snackbar(getString(R.string.secure_browsing_done, count)).show()
                        needReload()
                    } else {
                        snackbar(R.string.secure_browsing_failed).show()
                    }
                }
            }
            true
        }

        val remoteDns = findPreference<EditTextPreference>(Key.REMOTE_DNS)!!
        val directDns = findPreference<EditTextPreference>(Key.DIRECT_DNS)!!
        val enableDnsRouting = findPreference<SwitchPreference>(Key.ENABLE_DNS_ROUTING)!!
        val enableFakeDns = findPreference<SwitchPreference>(Key.ENABLE_FAKEDNS)!!

        val logLevel = findPreference<LongClickListPreference>(Key.LOG_LEVEL)!!
        val mtu = findPreference<MTUPreference>(Key.MTU)!!
        globalCustomConfig = findPreference(Key.GLOBAL_CUSTOM_CONFIG)!!
        globalCustomConfig.useConfigStore(Key.GLOBAL_CUSTOM_CONFIG)

        logLevel.dialogLayoutResource = R.layout.layout_loglevel_help
        logLevel.setOnPreferenceChangeListener { _, _ ->
            needRestart()
            true
        }
        logLevel.setOnLongClickListener {
            if (context == null) return@setOnLongClickListener true

            val view = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                var size = DataStore.logBufSize
                if (size == 0) size = 50
                setText(size.toString())
            }

            MaterialAlertDialogBuilder(requireContext()).setTitle("Log buffer size (kb)")
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DataStore.logBufSize = view.text.toString().toInt()
                    if (DataStore.logBufSize <= 0) DataStore.logBufSize = 50
                    needRestart()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        mixedPort.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        val metedNetwork = findPreference<Preference>(Key.METERED_NETWORK)!!
        if (Build.VERSION.SDK_INT < 28) {
            metedNetwork.remove()
        }
        isProxyApps = findPreference(Key.PROXY_APPS)!!
        isProxyApps.setOnPreferenceChangeListener { _, newValue ->
            startActivity(Intent(activity, AppManagerActivity::class.java))
            if (newValue as Boolean) DataStore.dirty = true
            newValue
        }

        val profileTrafficStatistics =
            findPreference<SwitchPreference>(Key.PROFILE_TRAFFIC_STATISTICS)!!
        val speedInterval = findPreference<SimpleMenuPreference>(Key.SPEED_INTERVAL)!!
        profileTrafficStatistics.isEnabled = speedInterval.value.toString() != "0"
        speedInterval.setOnPreferenceChangeListener { _, newValue ->
            profileTrafficStatistics.isEnabled = newValue.toString() != "0"
            needReload()
            true
        }

        serviceMode.setOnPreferenceChangeListener { _, _ ->
            if (DataStore.serviceState.started) SagerNet.stopService()
            true
        }

        val tunImplementation = findPreference<SimpleMenuPreference>(Key.TUN_IMPLEMENTATION)!!
        val resolveDestination = findPreference<SwitchPreference>(Key.RESOLVE_DESTINATION)!!
        val acquireWakeLock = findPreference<SwitchPreference>(Key.ACQUIRE_WAKE_LOCK)!!
        val enableClashAPI = findPreference<SwitchPreference>(Key.ENABLE_CLASH_API)!!
        enableClashAPI.setOnPreferenceChangeListener { _, newValue ->
            (activity as MainActivity?)?.refreshNavMenu(newValue as Boolean)
            needReload()
            true
        }

        mixedPort.onPreferenceChangeListener = reloadListener
        appendHttpProxy.onPreferenceChangeListener = reloadListener
        showDirectSpeed.onPreferenceChangeListener = reloadListener
        trafficSniffing.onPreferenceChangeListener = reloadListener
        bypassLan.onPreferenceChangeListener = reloadListener
        bypassLanInCore.onPreferenceChangeListener = reloadListener
        mtu.onPreferenceChangeListener = reloadListener

        enableFakeDns.onPreferenceChangeListener = reloadListener
        remoteDns.onPreferenceChangeListener = reloadListener
        directDns.onPreferenceChangeListener = reloadListener
        enableDnsRouting.onPreferenceChangeListener = reloadListener

        ipv6Mode.onPreferenceChangeListener = reloadListener
        allowAccess.onPreferenceChangeListener = reloadListener

        resolveDestination.onPreferenceChangeListener = reloadListener
        tunImplementation.onPreferenceChangeListener = reloadListener
        acquireWakeLock.onPreferenceChangeListener = reloadListener
        globalCustomConfig.onPreferenceChangeListener = reloadListener
    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
        if (::globalCustomConfig.isInitialized) {
            globalCustomConfig.notifyChanged()
        }
    }

    private fun handleInstallCaCertClick() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            try {
                startActivity(KeyChain.createInstallIntent())
            } catch (_: Exception) {
                Toast.makeText(ctx, R.string.cert_install_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }

        val selectedProfile = ProfileManager.getProfile(DataStore.selectedProxy)
        val httpBean = (selectedProfile?.requireBean() as? HttpBean)?.takeIf { !it.isTLS() }

        if (httpBean != null) {
            val profileName = selectedProfile.displayName()
            val host = httpBean.serverAddress.ifBlank { "127.0.0.1" }
            val port = httpBean.serverPort
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.cert_install_title)
                .setMessage(getString(R.string.cert_install_dialog_message_with_proxy, profileName, host, port))
                .setPositiveButton(R.string.cert_install_fetch_and_open) { _, _ ->
                    fetchProxyCaAndOpenSettings(profileName, httpBean)
                }
                .setNeutralButton(R.string.cert_install_open_settings) { _, _ ->
                    openSystemCaSettings()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.cert_install_title)
                .setMessage(R.string.cert_install_dialog_message)
                .setPositiveButton(R.string.cert_install_open_settings) { _, _ ->
                    openSystemCaSettings()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun fetchProxyCaAndOpenSettings(profileName: String, bean: HttpBean) {
        val appContext = context?.applicationContext ?: return
        val host = bean.serverAddress.ifBlank { "127.0.0.1" }
        val port = bean.serverPort
        Toast.makeText(appContext, R.string.cert_install_fetching, Toast.LENGTH_SHORT).show()

        runOnDefaultDispatcher {
            val downloaded = downloadCaFromHttpProxy(host, port, bean.username, bean.password)
            val savedResult = downloaded?.let { (x509, pemBytes) ->
                val safeLabel = profileName.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "Proxy" }
                val fileName = "Ember-CA-$safeLabel.crt"
                if (saveCertToDownloads(appContext, fileName, pemBytes)) {
                    val cn = Regex("CN=([^,]+)").find(x509.subjectX500Principal.name)?.groupValues?.get(1)
                        ?: x509.subjectX500Principal.name
                    fileName to cn
                } else null
            }

            onMainDispatcher {
                if (savedResult != null) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.cert_install_saved, savedResult.first, savedResult.second),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.cert_install_fetch_failed, host, port),
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (isAdded) {
                    openSystemCaSettings()
                }
            }
        }
    }

    private fun downloadCaFromHttpProxy(
        host: String,
        port: Int,
        username: String?,
        password: String?
    ): Pair<X509Certificate, ByteArray>? {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        val candidateUrls = listOf(
            "http://burp/cert",
            "http://mitm.it/cert/cer",
            "http://zap/OTHER/core/other/rootca/",
            "http://$host:$port/cert"
        )
        val authHeader = if (!username.isNullOrBlank()) {
            val token = Base64.encodeToString("$username:${password.orEmpty()}".toByteArray(), Base64.NO_WRAP)
            "Basic $token"
        } else null

        val certFactory = CertificateFactory.getInstance("X.509")
        for (urlStr in candidateUrls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 4000
                    instanceFollowRedirects = true
                    if (authHeader != null) {
                        setRequestProperty("Proxy-Authorization", authHeader)
                    }
                }
                if (conn.responseCode in 200..299) {
                    val rawBytes = conn.inputStream.use { it.readBytes() }
                    if (rawBytes.isNotEmpty() && rawBytes.size < 65536) {
                        val cert = certFactory.generateCertificate(ByteArrayInputStream(rawBytes)) as? X509Certificate
                        if (cert != null) {
                            val b64 = Base64.encodeToString(cert.encoded, Base64.DEFAULT).trim()
                            val pem = "-----BEGIN CERTIFICATE-----\n$b64\n-----END CERTIFICATE-----\n"
                            return cert to pem.toByteArray(Charsets.US_ASCII)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun saveCertToDownloads(
        appContext: android.content.Context,
        fileName: String,
        pemBytes: ByteArray
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = appContext.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            resolver.delete(
                collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                out.write(pemBytes)
                out.flush()
            } ?: return false
            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openSystemCaSettings() {
        val ctx = context ?: return
        val candidates = listOf(
            Intent("com.android.settings.MORE_SECURITY_PRIVACY_SETTINGS").apply {
                putExtra(":settings:fragment_args_key", "encryption_and_credential")
            },
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                putExtra(":settings:fragment_args_key", "encryption_and_credential")
            },
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(ctx, R.string.cert_install_failed, Toast.LENGTH_SHORT).show()
    }

}