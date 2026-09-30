package com.example.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles safe, validated device and application actions executed by Arushi.
 * Only predefined, allowlisted functions are permitted.
 * Arbitrary commands or shell actions are strictly forbidden.
 */
class DeviceActionHandler(
    private val context: Context,
    private val onLog: (tag: String, message: String) -> Unit = { tag, msg -> Log.d(tag, msg) }
) {
    companion object {
        private const val TAG = "DeviceActionHandler"

        // Allowlisted app packages and intents
        private val KNOWN_APPS = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "calculator" to "com.google.android.calculator"
        )
    }

    /**
     * Executes a tool call received from Gemini Live and returns a JSON response object.
     */
    fun executeAction(functionName: String, args: JSONObject): JSONObject {
        onLog(TAG, "Executing tool call: $functionName with args: $args")
        return try {
            when (functionName) {
                "openWhatsApp" -> openWhatsApp()
                "openApp" -> {
                    val appName = args.optString("appName", "")
                    openApp(appName)
                }
                "openUrl" -> {
                    val url = args.optString("url", "")
                    openUrl(url)
                }
                "makeCall" -> {
                    val phoneNumber = args.optString("phoneNumber", "")
                    makeCall(phoneNumber)
                }
                "callContact" -> {
                    val contactName = args.optString("contactName", "")
                    callContact(contactName)
                }
                else -> {
                    onLog(TAG, "Unknown tool function requested: $functionName")
                    JSONObject().apply {
                        put("success", false)
                        put("error", "Unsupported action: $functionName")
                    }
                }
            }
        } catch (e: Exception) {
            onLog(TAG, "Error executing action $functionName: ${e.message}")
            JSONObject().apply {
                put("success", false)
                put("error", "Failed to execute $functionName: ${e.message}")
            }
        }
    }

    fun openWhatsApp(): JSONObject {
        val result = JSONObject()
        result.put("action", "openWhatsApp")

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            onLog(TAG, "WhatsApp app opened successfully via launch intent")
            result.put("success", true)
            result.put("message", "WhatsApp application opened successfully")
            return result
        }

        // Try direct URI scheme
        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (uriIntent.resolveActivity(pm) != null) {
            context.startActivity(uriIntent)
            onLog(TAG, "WhatsApp opened via whatsapp:// scheme")
            result.put("success", true)
            result.put("message", "WhatsApp opened")
            return result
        }

        // Fallback to web WhatsApp or inform user
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (webIntent.resolveActivity(pm) != null) {
            context.startActivity(webIntent)
            onLog(TAG, "WhatsApp app not installed, opening web fallback")
            result.put("success", true)
            result.put("message", "WhatsApp app is not installed; opening WhatsApp Web in browser")
            return result
        }

        onLog(TAG, "WhatsApp is not installed on this device")
        result.put("success", false)
        result.put("error", "WhatsApp is not installed on this device")
        return result
    }

    fun openApp(appName: String): JSONObject {
        val result = JSONObject()
        result.put("action", "openApp")
        result.put("appName", appName)

        val cleanName = appName.trim().lowercase()
        val pm = context.packageManager

        // Check system apps
        when (cleanName) {
            "camera" -> {
                val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (cameraIntent.resolveActivity(pm) != null) {
                    context.startActivity(cameraIntent)
                    onLog(TAG, "Camera opened")
                    result.put("success", true)
                    result.put("message", "Camera opened successfully")
                    return result
                }
            }
            "clock", "alarm", "alarms" -> {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (clockIntent.resolveActivity(pm) != null) {
                    context.startActivity(clockIntent)
                    onLog(TAG, "Clock/Alarms opened")
                    result.put("success", true)
                    result.put("message", "Clock opened successfully")
                    return result
                }
            }
            "calendar" -> {
                val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (calendarIntent.resolveActivity(pm) != null) {
                    context.startActivity(calendarIntent)
                    onLog(TAG, "Calendar opened")
                    result.put("success", true)
                    result.put("message", "Calendar opened successfully")
                    return result
                }
            }
            "settings" -> {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                onLog(TAG, "Settings opened")
                result.put("success", true)
                result.put("message", "Settings opened successfully")
                return result
            }
        }

        // Check package map
        val packageName = KNOWN_APPS[cleanName]
        if (packageName != null) {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                onLog(TAG, "App $appName ($packageName) opened successfully")
                result.put("success", true)
                result.put("message", "$appName opened successfully")
                return result
            }

            // Web fallback for YouTube or Instagram
            if (cleanName == "youtube") {
                return openUrl("https://www.youtube.com")
            } else if (cleanName == "instagram") {
                return openUrl("https://www.instagram.com")
            }
        }

        onLog(TAG, "App '$appName' is not installed or supported")
        result.put("success", false)
        result.put("error", "App '$appName' could not be found or is not installed")
        return result
    }

    fun openUrl(url: String): JSONObject {
        val result = JSONObject()
        result.put("action", "openUrl")
        result.put("url", url)

        val cleanUrl = url.trim()
        val targetUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else {
            cleanUrl
        }

        return try {
            val uri = Uri.parse(targetUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onLog(TAG, "URL opened successfully: $targetUrl")
            result.put("success", true)
            result.put("message", "Opened $targetUrl in browser")
            result
        } catch (e: Exception) {
            onLog(TAG, "Failed to open URL $targetUrl: ${e.message}")
            result.put("success", false)
            result.put("error", "Failed to open URL: ${e.message}")
            result
        }
    }

    fun makeCall(phoneNumber: String): JSONObject {
        val result = JSONObject()
        result.put("action", "makeCall")
        result.put("phoneNumber", phoneNumber)

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) {
            onLog(TAG, "Invalid phone number provided: $phoneNumber")
            result.put("success", false)
            result.put("error", "Invalid phone number")
            return result
        }

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            onLog(TAG, "Dialer opened for number: $cleanNumber")
            result.put("success", true)
            result.put("message", "Opened phone dialer with number $cleanNumber")
            result
        } catch (e: Exception) {
            onLog(TAG, "Failed to initiate call to $cleanNumber: ${e.message}")
            result.put("success", false)
            result.put("error", "Could not open dialer: ${e.message}")
            result
        }
    }

    fun callContact(contactName: String): JSONObject {
        val result = JSONObject()
        result.put("action", "callContact")
        result.put("contactName", contactName)

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            onLog(TAG, "Contacts permission (READ_CONTACTS) is not granted")
            result.put("success", false)
            result.put("needsPermission", true)
            result.put("error", "Contacts permission is required to search contacts. Please grant Contacts permission.")
            return result
        }

        val cleanName = contactName.trim()
        if (cleanName.isEmpty()) {
            result.put("success", false)
            result.put("error", "Contact name cannot be empty")
            return result
        }

        data class ContactMatch(val name: String, val number: String)
        val matchingContacts = mutableListOf<ContactMatch>()

        try {
            val cr = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$cleanName%")

            cr.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: ""
                    val number = cursor.getString(numberIndex) ?: ""
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        // Avoid duplicates
                        if (matchingContacts.none { it.name == name && it.number == number }) {
                            matchingContacts.add(ContactMatch(name, number))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onLog(TAG, "Exception querying contacts: ${e.message}")
            result.put("success", false)
            result.put("error", "Error reading contacts: ${e.message}")
            return result
        }

        when (matchingContacts.size) {
            0 -> {
                onLog(TAG, "No contacts found matching '$cleanName'")
                result.put("success", false)
                result.put("error", "No contact found matching '$cleanName' in your phone contacts")
            }
            1 -> {
                val match = matchingContacts.first()
                onLog(TAG, "Exact contact match found: ${match.name} (${match.number})")
                val dialResult = makeCall(match.number)
                result.put("success", dialResult.optBoolean("success", true))
                result.put("matchedName", match.name)
                result.put("phoneNumber", match.number)
                result.put("message", "Calling ${match.name} at ${match.number}")
            }
            else -> {
                // Multiple contacts found: do NOT guess! Ask user which one
                onLog(TAG, "Multiple contacts found for '$cleanName': ${matchingContacts.map { it.name }}")
                result.put("success", false)
                result.put("multipleMatches", true)
                val namesArray = JSONArray()
                matchingContacts.take(5).forEach { namesArray.put("${it.name} (${it.number})") }
                result.put("matches", namesArray)
                result.put("error", "Found ${matchingContacts.size} contacts matching '$cleanName': ${matchingContacts.take(3).joinToString { it.name }}. Please ask user which contact they want to call.")
            }
        }
        return result
    }
}
