import { chromium } from 'playwright';

const OUT = new URL('../docs/screenshots', import.meta.url).pathname;
const BASE = 'http://localhost:8080';

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const page = await browser.newPage({ viewport: { width: 1440, height: 950 }, deviceScaleFactor: 2 });

async function shot(name, full = true) {
  await page.waitForTimeout(700);
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: full });
  console.log('captured', name);
}

// 1. Sign-in screen
await page.goto(`${BASE}/sign-in`, { waitUntil: 'networkidle' });
await shot('01-sign-in', false);

// Sign in
await page.fill('#username', 'reviewer');
await page.fill('#password', 'Review#2026');
await page.click('button[type=submit]');
await page.waitForURL('**/dashboard', { timeout: 10000 });

// 2. Dashboard
await shot('02-dashboard');

// 3. Provider queue
await page.click('a[href="/providers"]');
await page.waitForSelector('table');
await shot('03-provider-queue');

// 4. Upload screen, before and after processing a document
await page.click('a[href="/upload"]');
await page.waitForSelector('#file');
await shot('04-upload-form');
await page.setInputFiles('#file', new URL('../sample-data/documents/03-address-discrepancy.txt', import.meta.url).pathname);
await page.click('button[type=submit]');
await page.waitForTimeout(1200);
await shot('04b-upload-result');

// 5. Provider detail - discrepancy case
await page.goto(`${BASE}/providers/3`, { waitUntil: 'networkidle' });
await shot('05-provider-detail-discrepancies');

// 6. Provider detail - excluded provider
await page.goto(`${BASE}/providers/2`, { waitUntil: 'networkidle' });
await shot('06-provider-detail-excluded');

// 7. Review queue
await page.goto(`${BASE}/review`, { waitUntil: 'networkidle' });
await shot('07-review-queue');

// 8. Audit trail
await page.goto(`${BASE}/audit`, { waitUntil: 'networkidle' });
await shot('08-audit-trail');

// 9. Administration
await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
await shot('09-administration');

// 10. High-contrast mode on the dashboard
await page.goto(`${BASE}/dashboard`, { waitUntil: 'networkidle' });
await page.getByRole('button', { name: 'High contrast' }).click();
await page.getByRole('button', { name: 'Larger text' }).click();
await shot('10-accessibility-modes');

await browser.close();
