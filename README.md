# Actual Budget Bridge

A lightweight Android companion app that automatically captures transaction notifications from your banking and payment apps and syncs them directly into your personal [Actual Budget](https://actualbudget.org/) instance via [Actual Tap](https://github.com/MattFaz/actualtap).

---

## Prerequisites

Actual Budget Bridge requires both of the following installed and running on your server:
- **[Actual Budget](https://actualbudget.org/)** — your personal finance manager.
- **[Actual Tap](https://github.com/MattFaz/actualtap)** — the server-side API bridge for creating transactions.

---

## Features

- **⚡ Real-Time Capture**: Instantly detects new transactions from incoming banking and payment app notifications.
- **👆 Optional Review & Confirmation**: Review payee and amount with convenient 1-tap notification actions or a floating review dialog, or enable auto-send to bypass review and sync automatically.
- **📍 Location Tagging**: Optionally attach location context to transactions to remember where you made a purchase.
- **🔒 Privacy First**: Runs entirely on your device. Your data goes directly to your own Actual Budget setup—no third-party cloud or data collection.

---

## How It Works

1. **Spend**: You make a purchase, and your bank sends a notification.
2. **Review (Optional)**: Actual Budget Bridge catches the notification, extracts transaction details, and prompts you to confirm (or automatically syncs if auto-send is enabled).
3. **Sync**: The transaction is synced to your Actual Budget accounts via Actual Tap.

---

## Getting Started

### 1. Download & Install
Download the latest `actual-budget-bridge-*.apk` from the **[Releases](../../releases)** page and install it on your Android device.

### 2. Grant Permissions
When you open the app for the first time:
- Enable **Notification Access** so the app can detect bank alerts.
- Allow **Notifications** to receive quick confirmation prompts.
- *(Optional)* Grant **Location Permission** if you wish to tag where purchases occur.

### 3. Configure Your Server
Open the app settings and enter your Actual Tap server URL (with Actual Budget connected) and API token.

---

## Contributing & Issues

Have an idea, found a bug, or want support for a new banking app? Feel free to open an issue or submit a pull request!
