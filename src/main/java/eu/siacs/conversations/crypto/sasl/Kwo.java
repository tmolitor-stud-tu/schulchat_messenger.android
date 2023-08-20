package eu.siacs.conversations.crypto.sasl;

import android.util.Log;
import android.content.Context;
import android.util.Base64;
import android.os.Build;
import android.provider.Settings;
import android.bluetooth.BluetoothAdapter;
import android.content.ContextWrapper;

import java.nio.charset.Charset;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;

import eu.siacs.conversations.R;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.PhoneHelper;

public class Kwo extends SaslMechanism {
    public static final String MECHANISM = "KWO";
    
    private static final String LOGTAG = "KWO_SASL";

    public Kwo(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 30;		//highest prio (SCRAM-SHA256 and EXTERNAL have prio 25, SCRAM-SHA1 has prio 20)
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public String getClientFirstMessage(final SSLSocket sslSocket) {
        return Base64.encodeToString(account.getUsername().getBytes(Charset.defaultCharset()), Base64.NO_WRAP);
    }
    
    @Override
    public String getResponse(final String challenge, final SSLSocket sslSocket) throws AuthenticationException {
        if(challenge == null)
            return null;
        return getMessage(account.getPassword(), this.account.context, new String(Base64.decode(challenge, Base64.DEFAULT)));
    }

    private static String getMessage(final String password, Context context, final String challenge) {
        String pw = createPassword(password, context, challenge);
        Log.d(LOGTAG, pw);
        return Base64.encodeToString(pw.getBytes(Charset.defaultCharset()), Base64.NO_WRAP);
    }
    
    private static String createPassword(final String password, Context context, final String challenge) {
        StringBuilder pw = new StringBuilder("");
        try {
            String deviceName = CryptoHelper.bytesToHex(getDeviceName(context).getBytes("UTF-8"));
            pw.append(PhoneHelper.getAndroidId(context));
            pw.append("|");
            pw.append(deviceName);
            pw.append("|");
            pw.append(BuildConfig.VERSION_CODE);
            pw.append("|");
            pw.append("android");
            //hmac all of this using several keys and add the resulting hash to our passwd string
            String hash = hash_hmac(hash_hmac(hash_hmac(pw.toString(), password), BuildConfig.APP_SECRET), challenge);
            pw.append("|" + hash);
            return pw.toString();
        } catch (Exception e) {
            e.printStackTrace();
            String trace = "";
            for(StackTraceElement el : e.getStackTrace())
                trace=trace+el.toString();
            return "ERROR: "+e.getMessage()+" "+trace;
        }
    }
    
    private static String hash_hmac(String text, String key) throws NoSuchAlgorithmException {
        Mac sha256_HMAC = null;
        try {
            sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return bytesToString(sha256_HMAC.doFinal(text.getBytes()));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private static String bytesToString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    //see https://medium.com/capital-one-tech/how-to-get-an-android-device-nickname-d5eab12f4ced
    private static String getDeviceName(Context context) {
        String name = "Unknown device";
        
        //try to get user-settable device name, see https://medium.com/capital-one-tech/how-to-get-an-android-device-nickname-d5eab12f4ced
        //if more than one of this returns a name, the last one that is not null (or throws an exception) wins
        try {
            name = UseStringIfNotNull(name, Settings.System.getString(context.getContentResolver(), "bluetooth_name"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            name = UseStringIfNotNull(name, Settings.Secure.getString(context.getContentResolver(), "bluetooth_name"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            name = UseStringIfNotNull(name, BluetoothAdapter.getDefaultAdapter().getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            name = UseStringIfNotNull(name, Settings.System.getString(context.getContentResolver(), "device_name"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            //the next one is taken from a commentary int the medium.com blog post, not from the main article
            name = UseStringIfNotNull(name, Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME));
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            name = UseStringIfNotNull(name, Settings.Secure.getString(context.getContentResolver(), "lock_screen_owner_info"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        //add manufacturer and build
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return name!=null && name.length()>0 ? name + " (" + model + ")" : model;
        } else {
            return name!=null && name.length()>0 ? name + " (" + manufacturer + " " + model + ")" : manufacturer + " " + model;
        }
    }
    
    private static String UseStringIfNotNull(String oldstr, String newstr)
    {
        Log.d(LOGTAG, "DEVIE NAME: '"+(oldstr == null || oldstr.length() == 0 ? "" : oldstr)+"' --> '"+(newstr == null || newstr.length() == 0 ? "" : newstr)+"'");
        return newstr == null || newstr.length() == 0 ? oldstr : newstr;
    }

}