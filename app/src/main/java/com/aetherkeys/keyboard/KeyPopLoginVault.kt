package com.aetherkeys.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.WindowManager
import java.security.MessageDigest
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File
import java.net.URI
import java.util.Locale

data class VaultAccount(val id:String,val target:String,val label:String,
                        val username:String,val password:String,val created:Long,
                        val appSigningDigest:String="")

/** Require the installed Android app's signing certificate when filling native logins. */
object KeyPopAppIdentity {
    fun signingDigest(context:Context, packageId:String):String {
        return runCatching {
            @Suppress("DEPRECATION")
            val info=context.packageManager.getPackageInfo(packageId,
                if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val signers=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners.orEmpty()
                else info.signatures.orEmpty()
            if(signers.isEmpty())return@runCatching ""
            signers.map{ cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                    .joinToString(""){ b -> "%02x".format(b.toInt() and 255) }
            }.sorted().joinToString(":")
        }.getOrDefault("")
    }
}

object VaultTarget {
    fun canonical(value:String):String {
        val text=value.trim().lowercase(Locale.ROOT)
        val host=if(text.startsWith("https://")) runCatching { URI(text).host.orEmpty() }.getOrDefault("") else text
        if(!Regex("[a-z0-9][a-z0-9_.-]{2,245}").matches(host)||!host.contains('.'))return ""
        if(host.contains(".."))return ""
        return host
    }
    fun matches(saved:String,actual:String):Boolean= saved.isNotEmpty() && saved==canonical(actual)
}

/** No account data leaves the device; OS-backed AES-GCM protects the saved file. */
class KeyPopLoginVault(private val context:Context) {
    private val file=File(context.filesDir,"keypop-logins.enc")
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("KeyPopCredentialVault",null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("KeyPopCredentialVault",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    private fun encrypt(bytes:ByteArray):ByteArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        return cipher.iv+cipher.doFinal(bytes)
    }
    private fun decrypt(bytes:ByteArray):ByteArray {
        require(bytes.size>=29) { "Encrypted vault is damaged" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
    @Synchronized fun list():List<VaultAccount> {
        if(!file.exists())return emptyList()
        val array=JSONArray(String(decrypt(file.readBytes()),Charsets.UTF_8))
        return List(array.length()){ i->
            val json=array.getJSONObject(i)
            VaultAccount(json.getString("id"),json.getString("target"),json.optString("label"),
                json.getString("username"),json.getString("password"),json.getLong("created"),
                json.optString("appSigningDigest"))
        }.sortedByDescending{it.created}
    }
    private fun persist(logins:List<VaultAccount>) {
        val array=JSONArray()
        for(v in logins) array.put(JSONObject().apply {
            put("id",v.id);put("target",v.target);put("label",v.label)
            put("username",v.username);put("password",v.password);put("created",v.created)
            put("appSigningDigest",v.appSigningDigest)
        })
        val temp=File(context.filesDir,"keypop-logins.tmp")
        temp.writeBytes(encrypt(array.toString().toByteArray(Charsets.UTF_8)))
        if(!temp.renameTo(file)) {temp.delete();error("Could not save encrypted vault") }
    }
    @Synchronized fun save(target:String,label:String,user:String,password:String):Boolean {
        val canonical=VaultTarget.canonical(target)
        if(canonical.isBlank()||user.isBlank()||password.isBlank()||password.length>4096)return false
        val all=list().toMutableList()
        val index=all.indexOfFirst{it.target==canonical && it.username==user}
        val account=VaultAccount(if(index>=0)all[index].id else UUID.randomUUID().toString(),
            canonical,label.take(80),user.take(300),password,System.currentTimeMillis(),
            KeyPopAppIdentity.signingDigest(context,canonical))
        if(index>=0)all[index]=account else all.add(account)
        persist(all)
        return true
    }
    @Synchronized fun mergeAll(imported:List<VaultAccount>):Int {
        val all=list().toMutableList()
        var added=0
        for(account in imported){
            if(all.any{it.id==account.id ||
                    (it.target==account.target && it.username==account.username)})continue
            all.add(account);added++
        }
        if(added>0)persist(all)
        return added
    }
    @Synchronized fun delete(id:String) { persist(list().filterNot{it.id==id}) }
    @Synchronized fun find(target:String):List<VaultAccount> = list().filter{VaultTarget.matches(it.target,target)}
    @Synchronized fun findVerifiedApp(target:String,signer:String):List<VaultAccount> =
        if(signer.isEmpty())emptyList() else find(target).filter{
            it.appSigningDigest.isNotBlank() && it.appSigningDigest==signer
        }
    @Synchronized fun get(id:String)=list().firstOrNull{it.id==id}
    @Synchronized fun rebindNativeApp(id:String):Boolean {
        val all=list().toMutableList()
        val at=all.indexOfFirst{it.id==id}
        if(at<0)return false
        val signer=KeyPopAppIdentity.signingDigest(context,all[at].target)
        if(signer.isEmpty())return false
        all[at]=all[at].copy(appSigningDigest=signer)
        persist(all)
        return true
    }
}

/** A device lock is mandatory before viewing or editing saved accounts. */
class KeyPopVaultActivity:Activity() {
    private val request=70
    private val exportRequest=72
    private val importRequest=73
    private var pendingExportPassword:String?=null
    private val vault by lazy{KeyPopLoginVault(this)}
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val km=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if(!km.isDeviceSecure) {
            Toast.makeText(this,"Configure a device screen lock first",Toast.LENGTH_LONG).show()
            finish();return
        }
        @Suppress("DEPRECATION")
        val intent=km.createConfirmDeviceCredentialIntent("KeyPop Login Vault","Unlock your saved accounts")
        if(intent==null){finish();return}
        @Suppress("DEPRECATION")
        startActivityForResult(intent,request)
    }
    @Deprecated("Activity Result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        when(requestCode){
            request->if(resultCode==RESULT_OK)render()else finish()
            exportRequest->{
                val password=pendingExportPassword
                pendingExportPassword=null
                if(resultCode==RESULT_OK && data?.data!=null && password!=null){
                    val status=runCatching{KeyPopPortableVaultBackup.export(this,data.data!!,password)}
                    Toast.makeText(this,status.fold(
                        {"Encrypted backup saved: ${it.logins} logins and ${it.profiles} form profiles"},
                        {"Backup failed. Existing data was not deleted."}),Toast.LENGTH_LONG).show()
                }
            }
            importRequest->if(resultCode==RESULT_OK && data?.data!=null){
                val uri=data.data!!
                askBackupPassword(false){password->
                    val status=runCatching{KeyPopPortableVaultBackup.restore(this,uri,password)}
                    Toast.makeText(this,status.fold(
                        {"Restored ${it.logins} new logins and ${it.profiles} new form profiles"},
                        {"Incorrect backup password or invalid backup file"}),Toast.LENGTH_LONG).show()
                    if(status.isSuccess)render()
                }
            }
        }
    }
    override fun onDestroy(){pendingExportPassword=null;super.onDestroy()}
    private fun askBackupPassword(export:Boolean,accepted:(String)->Unit){
        val layout=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(6),dp(16),dp(6))
        }
        fun box(hintText:String)=EditText(this).apply{
            hint=hintText;setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layout.addView(this)
        }
        val first=box("Backup password (at least 12 characters)")
        val confirm=if(export)box("Confirm backup password")else null
        val dialog=AlertDialog.Builder(this)
            .setTitle(if(export)"Create encrypted vault backup" else "Unlock encrypted backup")
            .setMessage("Includes saved passwords and form profiles. Keep your backup and its separate password safe.")
            .setView(layout).setNegativeButton("Cancel",null)
            .setPositiveButton(if(export)"Continue" else "Restore",null).create()
        dialog.setOnShowListener{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val word=first.text.toString()
                if(word.length<12 || (confirm!=null && word!=confirm.text.toString())){
                    Toast.makeText(this,"Enter matching backup passwords of 12+ characters",Toast.LENGTH_LONG).show()
                }else{dialog.dismiss();accepted(word)}
            }
        }
        dialog.show()
    }
    private fun exportVault(){
        askBackupPassword(true){password->
            pendingExportPassword=password
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
                addCategory(Intent.CATEGORY_OPENABLE)
                type="application/octet-stream"
                putExtra(Intent.EXTRA_TITLE,"KeyPop-Private-Vault.kpv")
            },exportRequest)
        }
    }
    private fun importVault(){
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);type="*/*"
        },importRequest)
    }
    private fun render() {
        val screen=ScrollView(this)
        val col=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(24),dp(18),dp(24))
        }
        screen.addView(col);setContentView(screen)
        col.addView(TextView(this).apply {text="KeyPop · Secure Login Vault";textSize=23f})
        col.addView(TextView(this).apply {
            text="Your accounts are encrypted locally. Add an exact website hostname or Android package. Compatible apps can request Autofill; filling always requires device authentication."
            textSize=13f;setPadding(0,dp(13),0,dp(12))
        })
        col.addView(Button(this).apply{text="＋ Add a login";setOnClickListener{addLogin()}})
        col.addView(Button(this).apply{
            text="Saved form data · Names, email, phone and addresses"
            setOnClickListener{startActivity(Intent(this@KeyPopVaultActivity,KeyPopFormProfilesActivity::class.java))}
        })
        col.addView(Button(this).apply{
            text="Enable KeyPop as Android Autofill provider"
            setOnClickListener{
                val intent=Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply{
                    data=Uri.parse("package:$packageName")
                }
                runCatching{startActivity(intent)}.onFailure{
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        })
        col.addView(Button(this).apply{
            text="Export encrypted logins & forms backup"
            setOnClickListener{exportVault()}
        })
        col.addView(Button(this).apply{
            text="Import encrypted logins & forms backup"
            setOnClickListener{importVault()}
        })
        col.addView(TextView(this).apply{
            text="Normal stablev2 upgrades preserve private data. Before uninstalling or switching app identities, export an encrypted vault backup. Older preview data cannot be read automatically from this app."
            textSize=12f
        })
        try {
            val saved=vault.list()
            if(saved.isEmpty())col.addView(TextView(this).apply{text="No saved logins yet."})
            for(entry in saved) {
                col.addView(TextView(this).apply {
                    text=entry.target+" · "+entry.label+"\n"+entry.username+"   ·   Password: ••••••••"
                    textSize=15f;setPadding(0,dp(14),0,dp(6))
                })
                if(entry.appSigningDigest.isEmpty()){
                    col.addView(Button(this).apply{
                        text="Verify native app for secure filling (if installed)"
                        setOnClickListener{
                            val ok=runCatching{vault.rebindNativeApp(entry.id)}.getOrDefault(false)
                            Toast.makeText(this@KeyPopVaultActivity,
                                if(ok)"App certificate verified" else "App identity unavailable. Websites do not require this.",
                                Toast.LENGTH_LONG).show()
                            if(ok)render()
                        }
                    })
                }
                col.addView(Button(this).apply {
                    text="Delete this account"
                    setOnClickListener{
                        AlertDialog.Builder(this@KeyPopVaultActivity).setTitle("Delete account?")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Delete"){_,_->vault.delete(entry.id);render()}.show()
                    }
                })
            }
        }catch(_:Exception){
            col.addView(TextView(this).apply {
                text="Cannot decrypt saved credentials. Existing data was not overwritten."
                setTextColor(android.graphics.Color.RED)
            })
        }
    }
    private fun addLogin() {
        val form=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),0)}
        fun field(hint:String,kind:Int):EditText {
            val view=EditText(this).apply{
                this.hint=hint;inputType=kind;setSingleLine(true)
            }
            form.addView(view,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(53)))
            return view
        }
        val host=field("example.com or com.example.app",InputType.TYPE_CLASS_TEXT)
        val label=field("Nickname (optional)",InputType.TYPE_CLASS_TEXT)
        val username=field("Username or email",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password=field("Password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val scroll=ScrollView(this).apply{addView(form)}
        AlertDialog.Builder(this).setTitle("Add secure login").setView(scroll)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save"){_,_->
                val ok=runCatching{vault.save(host.text.toString(),label.text.toString(),
                    username.text.toString(),password.text.toString())}.getOrDefault(false)
                Toast.makeText(this,if(ok)"Login saved" else "Could not save account",Toast.LENGTH_LONG).show()
                if(ok)render()
            }.show()
    }
}
