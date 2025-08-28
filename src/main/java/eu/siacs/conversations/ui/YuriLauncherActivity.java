package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.XmppUriLauncher;
import eu.siacs.conversations.xmpp.Jid;

public class YuriLauncherActivity extends AppCompatActivity {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final var intent = getIntent();
        final var data = intent == null ? null : intent.getData();
        if (data == null) {
            new Handler(Looper.getMainLooper()).post(this::finish);
            return;
        }
        //KWO: handle provisioning url before the regular xmpp uri handling
        if (handleProvisioningUri(data)) {
            new Handler(Looper.getMainLooper()).post(this::finish);
            return;
        }
        final MiniUri uri;
        try {
            uri = MiniUri.tryInternalParse(data.toString());
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG, "could not parse mini uri", e);
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_LONG).show();
            new Handler(Looper.getMainLooper()).post(this::finish);
            return;
        }
        final MiniUri.Xmpp xmpp;
        if (uri instanceof MiniUri.Xmpp x) {
            xmpp = x;
        } else if (uri instanceof MiniUri.Transformable t
                && t.transform() instanceof MiniUri.Xmpp x) {
            xmpp = x;
        } else {
            Log.d(Config.LOGTAG, "mini uri is of unknown type: " + uri.getClass().getSimpleName());
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_LONG).show();
            new Handler(Looper.getMainLooper()).post(this::finish);
            return;
        }
        final var launcher = new XmppUriLauncher(this);
        launcher.launch(xmpp);
        new Handler(Looper.getMainLooper()).post(this::finish);
    }

    //KWO: a provisioning url carries user/domain/token and is only honoured while no account
    //exists yet; with an account present we fall through to the regular xmpp uri handling.
    private boolean handleProvisioningUri(final Uri data) {
        final var accounts = DatabaseBackend.getInstance(this).getAccountAddresses();
        if (!accounts.isEmpty()) {
            return false;
        }
        final Uri result = Uri.parse(Uri.decode("http://example.com?" + data.toString()));
        final String user = result.getQueryParameter("user");
        final String domain = result.getQueryParameter("domain");
        if (user == null || domain == null) {
            Log.d(Config.LOGTAG, "not handling uri, no user or domain query params given: " + data);
            return false;
        }
        if (!domain.toLowerCase().endsWith(Config.MAGIC_CREATE_DOMAIN)) {
            showError(R.string.account_registrations_are_not_supported);
            return true;
        }
        final Jid jid = Jid.ofLocalAndDomain(user, domain);
        if (jid.getLocal() != null && accounts.contains(jid.asBareJid())) {
            showError(R.string.account_already_exists);
            return true;
        }
        final Intent serviceIntent = new Intent(this, XmppConnectionService.class);
        serviceIntent.setAction(XmppConnectionService.ACTION_PROVISION_ACCOUNT);
        serviceIntent.putExtra("address", jid.asBareJid().toString());
        serviceIntent.putExtra("password", result.getQueryParameter("token"));
        Compatibility.startService(this, serviceIntent);
        final Intent editAccount = new Intent(this, EditAccountActivity.class);
        editAccount.putExtra("jid", jid.asBareJid().toString());
        editAccount.putExtra("init", true);
        startActivity(editAccount);
        return true;
    }

    private void showError(final int error) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }
}
