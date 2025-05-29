// functions/index.js
const functions       = require('firebase-functions');
const admin           = require('firebase-admin');
const { CloudTasksClient } = require('@google-cloud/tasks');

admin.initializeApp();

// your HTTP‐triggered sendDailyReminder
exports.sendDailyReminder = functions.https.onRequest(async (req, res) => {
  const message = {
    topic: 'daily_reminder',
    android: {
      // ask FCM to do a high-priority (heads-up) delivery
      priority: 'high',

      notification: {
        channelId: 'daily_notification_channel',   // must match your Android channel
        title:      'BeJam – Zeit für deinen Song!',
        body:       'Time to post your daily song 🎵',
        // on Android < O, you can also bump priority here:
        // priority: 'high'
      }
    }
  };

  try {
    const response = await admin.messaging().send(message);
    console.log('✅ Successfully sent message:', response);

    // --- DELETE TODAY'S DAILY SELECTIONS ---
    const db = admin.firestore();
    const now = new Date();
    const startOfDay = new Date(now.setHours(0,0,0,0)).getTime();
    const endOfDay   = new Date(now.setHours(24,0,0,0)).getTime();
    const snap = await db.collection('daily_selections')
      .where('timestamp', '>=', startOfDay)
      .where('timestamp', '<', endOfDay)
      .get();
    const batch = db.batch();
    snap.docs.forEach(doc => batch.delete(doc.ref));
    await batch.commit();
    console.log(`Cleared ${snap.size} daily selections.`);
    // --- END DELETE ---

    res.status(200).send('OK');
  } catch (err) {
    console.error('❌ Error sending message:', err);
    res.status(500).send(err.toString());
  }
});

// the Pub/Sub “beat” at 08:00 that enqueues a single Cloud Task at a random time
exports.scheduleRandomDailyReminder = functions.pubsub
  .schedule('every day 08:00')
  .timeZone('Europe/Berlin')
  .onRun(async () => {
    const client   = new CloudTasksClient();
    // ← use the env var that actually exists:
    const project  = process.env.GOOGLE_CLOUD_PROJECT || process.env.GCLOUD_PROJECT;
    const location = 'us-central1';
    const queue    = 'daily-reminder-queue';
    const url      = `https://${location}-${project}.cloudfunctions.net/sendDailyReminder`;

    const now   = new Date();
    const start = new Date(now); start.setHours(8,0,0,0);
    const end   = new Date(now); end.setHours(24,0,0,0);
    const randomMs     = start.getTime() + Math.random()*(end.getTime()-start.getTime());
    const scheduleTime = { seconds: Math.floor(randomMs/1000) };

    const parent = client.queuePath(project, location, queue);
    const task   = { httpRequest: { httpMethod:'POST', url, headers:{'Content-Type':'application/json'} },
                     scheduleTime };
    const [response] = await client.createTask({ parent, task });
    console.log(`🗓️ Enqueued task ${response.name} at ${new Date(randomMs).toISOString()}`);
  });