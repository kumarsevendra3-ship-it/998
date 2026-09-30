/**
 * Safe device and application action executor for Arushi.
 * Only predefined allowlisted operations are permitted.
 * Arbitrary commands or script evaluation are strictly disallowed.
 */

export interface ActionResult {
  success: boolean;
  action: string;
  message?: string;
  error?: string;
  data?: Record<string, unknown>;
}

const KNOWN_WEB_APPS: Record<string, { appUrl: string; webUrl: string }> = {
  whatsapp: {
    appUrl: 'whatsapp://send',
    webUrl: 'https://web.whatsapp.com',
  },
  youtube: {
    appUrl: 'vnd.youtube://',
    webUrl: 'https://www.youtube.com',
  },
  instagram: {
    appUrl: 'instagram://app',
    webUrl: 'https://www.instagram.com',
  },
  maps: {
    appUrl: 'geo:0,0?q=',
    webUrl: 'https://www.google.com/maps',
  },
  'google maps': {
    appUrl: 'geo:0,0?q=',
    webUrl: 'https://www.google.com/maps',
  },
  calculator: {
    appUrl: 'calc://',
    webUrl: 'https://www.google.com/search?q=calculator',
  },
  calendar: {
    appUrl: 'content://com.android.calendar/time/',
    webUrl: 'https://calendar.google.com',
  },
  chrome: {
    appUrl: 'googlechrome://',
    webUrl: 'https://www.google.com',
  },
};

export async function executeDeviceAction(
  name: string,
  args: Record<string, unknown>
): Promise<ActionResult> {
  console.log(`[DeviceAction] Executing tool '${name}' with args:`, args);

  switch (name) {
    case 'openWhatsApp': {
      try {
        const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
        if (isMobile) {
          window.location.href = 'whatsapp://send';
        } else {
          window.open('https://web.whatsapp.com', '_blank', 'noopener,noreferrer');
        }
        return {
          success: true,
          action: 'openWhatsApp',
          message: 'WhatsApp application opened successfully',
        };
      } catch (err: unknown) {
        return {
          success: false,
          action: 'openWhatsApp',
          error: `Could not open WhatsApp: ${err instanceof Error ? err.message : String(err)}`,
        };
      }
    }

    case 'openApp': {
      const appName = String(args.appName || '').trim().toLowerCase();
      if (!appName) {
        return {
          success: false,
          action: 'openApp',
          error: 'No application name specified',
        };
      }

      const match = KNOWN_WEB_APPS[appName];
      if (match) {
        const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
        if (isMobile && match.appUrl) {
          window.location.href = match.appUrl;
        } else {
          window.open(match.webUrl, '_blank', 'noopener,noreferrer');
        }
        return {
          success: true,
          action: 'openApp',
          message: `${args.appName} opened successfully`,
        };
      }

      return {
        success: false,
        action: 'openApp',
        error: `App '${args.appName}' is not in the supported applications list.`,
      };
    }

    case 'openUrl': {
      const rawUrl = String(args.url || '').trim();
      if (!rawUrl) {
        return {
          success: false,
          action: 'openUrl',
          error: 'URL cannot be empty',
        };
      }

      let validUrl = rawUrl;
      if (!validUrl.startsWith('http://') && !validUrl.startsWith('https://')) {
        validUrl = 'https://' + validUrl;
      }

      try {
        const parsed = new URL(validUrl);
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
          return {
            success: false,
            action: 'openUrl',
            error: 'Only HTTP and HTTPS web URLs are permitted',
          };
        }

        window.open(parsed.href, '_blank', 'noopener,noreferrer');
        return {
          success: true,
          action: 'openUrl',
          message: `Opened ${parsed.href} in browser`,
        };
      } catch {
        return {
          success: false,
          action: 'openUrl',
          error: `Invalid URL format: ${rawUrl}`,
        };
      }
    }

    case 'makeCall': {
      const phoneNumber = String(args.phoneNumber || '').trim();
      const cleanNumber = phoneNumber.replace(/[^0-9+]/g, '');
      if (!cleanNumber) {
        return {
          success: false,
          action: 'makeCall',
          error: 'Invalid or missing phone number',
        };
      }

      window.location.href = `tel:${cleanNumber}`;
      return {
        success: true,
        action: 'makeCall',
        message: `Phone dialer opened with number ${cleanNumber}`,
      };
    }

    case 'callContact': {
      const contactName = String(args.contactName || '').trim();
      if (!contactName) {
        return {
          success: false,
          action: 'callContact',
          error: 'Contact name cannot be empty',
        };
      }

      // Check if navigator.contacts is available (Chrome Android)
      const navWithContacts = navigator as unknown as {
        contacts?: { select: (props: string[], opts?: { multiple?: boolean }) => Promise<Array<{ name: string[]; tel: string[] }>> };
      };

      if (navWithContacts.contacts && 'select' in navWithContacts.contacts) {
        try {
          const selected = await navWithContacts.contacts.select(['name', 'tel'], { multiple: false });
          if (selected && selected.length > 0 && selected[0].tel && selected[0].tel.length > 0) {
            const num = selected[0].tel[0].replace(/[^0-9+]/g, '');
            window.location.href = `tel:${num}`;
            return {
              success: true,
              action: 'callContact',
              message: `Calling ${selected[0].name?.[0] || contactName} at ${num}`,
            };
          }
        } catch {
          // User cancelled contact selector or not allowed
        }
      }

      // If in browser without direct contacts read access:
      // Return clear explanation to Gemini so Arushi can speak naturally
      return {
        success: false,
        action: 'callContact',
        error: `Could not access contacts directly in this browser session. Please ask the user to provide the phone number to call ${contactName}.`,
      };
    }

    default:
      return {
        success: false,
        action: name,
        error: `Unknown action: ${name}`,
      };
  }
}
