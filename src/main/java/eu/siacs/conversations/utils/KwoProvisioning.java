package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.EditAccountActivity;
import eu.siacs.conversations.xmpp.Jid;

//KWO: provisioning links used to be handled by UriHandlerActivity, which upstream removed. The
//logic lives here now so both entry points can share it: view intents (YuriLauncherActivity) and
//scanned qr codes (QrCodeProcessingActivity).
public final class KwoProvisioning {

    private KwoProvisioning() {}

    //KWO: a provisioning url carries user/domain/token. With accounts already present an xmpp:
    //uri keeps going to the regular handler, but any other scheme is still treated as a
    //provisioning link so that further accounts can be added. Returns true when handled.
    public static boolean handle(final Activity activity, final Uri data) {
        if (data == null) {
            return false;
        }
        final var accounts = DatabaseBackend.getInstance(activity).getAccountAddresses();
        Log.d(Config.LOGTAG, "KwoProvisioning: checking accounts and uri scheme for " + data);
        if (!accounts.isEmpty() && "xmpp".equalsIgnoreCase(data.getScheme())) {
            return false;
        }
        final Uri result = asQueryUri(data);
        if (result == null) {
            return false;
        }
        final String user = result.getQueryParameter("user");
        final String domain = result.getQueryParameter("domain");
        if (user == null || domain == null) {
            Log.d(Config.LOGTAG, "not handling uri, no user or domain query params given: " + data);
            return false;
        }
        if (!domain.toLowerCase().endsWith(Config.MAGIC_CREATE_DOMAIN)) {
            showError(activity, R.string.account_registrations_are_not_supported);
            return true;
        }
        final Jid jid = Jid.ofLocalAndDomain(user, domain);
        if (jid.getLocal() != null && accounts.contains(jid.asBareJid())) {
            showError(activity, R.string.account_already_exists);
            return true;
        }
        provisionAccount(activity, jid, result.getQueryParameter("token"));
        return true;
    }

    //KWO: kwologin://user:token@domain keeps its parts in the scheme specific part, every other
    //uri already carries them as query parameters. Normalize both onto a dummy http url so the
    //values can be read back with getQueryParameter(). Returns null if the uri is unusable.
    private static Uri asQueryUri(final Uri data) {
        if (!"kwologin".equalsIgnoreCase(data.getScheme())) {
            return Uri.parse(Uri.decode("http://example.com?" + data));
        }
        final String[] userAndRest = data.getSchemeSpecificPart().split("@");
        if (userAndRest.length != 2) {
            Log.d(Config.LOGTAG, "not handling uri, could not parse domain: " + data);
            return null;
        }
        final String[] userAndToken = userAndRest[0].split(":");
        if (userAndToken.length != 2) {
            Log.d(Config.LOGTAG, "not handling uri, could not parse user or token: " + data);
            return null;
        }
        final String user = userAndToken[0].replaceFirst("^/+", "");
        final String token = userAndToken[1];
        final String domain = userAndRest[1].replaceFirst("/+$", "");
        final String query = "user=" + user + "&token=" + token + "&domain=" + domain;
        return Uri.parse(Uri.decode("http://example.com?" + query));
    }

    //KWO: this block is taken from the removed ProvisioningUtils
    private static void provisionAccount(
            final Activity activity, final Jid jid, final String token) {
        final Intent serviceIntent = new Intent(activity, XmppConnectionService.class);
        serviceIntent.setAction(XmppConnectionService.ACTION_PROVISION_ACCOUNT);
        serviceIntent.putExtra("address", jid.asBareJid().toString());
        serviceIntent.putExtra("password", token);
        Compatibility.startService(activity, serviceIntent);
        final Intent editAccount = new Intent(activity, EditAccountActivity.class);
        editAccount.putExtra("jid", jid.asBareJid().toString());
        editAccount.putExtra("init", true);
        activity.startActivity(editAccount);
    }

    private static void showError(final Activity activity, final int error) {
        Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
    }
}
