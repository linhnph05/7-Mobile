import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

function base64Url(input: Uint8Array | string) {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : input;
  const b64 = btoa(String.fromCharCode(...bytes));
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function utf8ToUint8(input: string) {
  return new TextEncoder().encode(input);
}

function pemToUint8Array(pem: string): Uint8Array {
  const b64 = pem.replace(/-----BEGIN [^-]+-----/, '')
                 .replace(/-----END [^-]+-----/, '')
                 .replace(/\s+/g, '');
  const binary = atob(b64);
  const len = binary.length;
  const buf = new Uint8Array(len);
  for (let i = 0; i < len; i++) buf[i] = binary.charCodeAt(i);
  return buf;
}

async function signJwtRS256(privateKeyPem: string, signingInput: string): Promise<string> {
  const pkcs8 = pemToUint8Array(privateKeyPem);
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pkcs8.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, utf8ToUint8(signingInput));
  return base64Url(new Uint8Array(sig));
}

async function fetchAccessToken(sa: any): Promise<{ access_token: string, expires_in: number }>{
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600;

  const header = { alg: 'RS256', typ: 'JWT' };
  const scope = 'https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/cloud-platform';
  const payload = {
    iss: sa.client_email,
    scope: scope,
    aud: sa.token_uri,
    exp: exp,
    iat: iat
  };

  const headerB64 = base64Url(JSON.stringify(header));
  const payloadB64 = base64Url(JSON.stringify(payload));
  const signingInput = headerB64 + '.' + payloadB64;

  const signature = await signJwtRS256(sa.private_key, signingInput);
  const assertion = signingInput + '.' + signature;

  const form = new URLSearchParams();
  form.append('grant_type', 'urn:ietf:params:oauth:grant-type:jwt-bearer');
  form.append('assertion', assertion);

  const tokenRes = await fetch(sa.token_uri, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form.toString()
  });
  if (!tokenRes.ok) {
    const text = await tokenRes.text();
    throw new Error('Failed to obtain access token: ' + tokenRes.status + ' ' + text);
  }
  return await tokenRes.json();
}

serve(async (req: Request) => {
  try {
    // 1. Nhận dữ liệu từ Webhook (Supabase trả về { type: "INSERT", record: { ... } })
    const body = await req.json();
    console.log("Received Webhook Data:", JSON.stringify(body));

    // Lấy dữ liệu của dòng vừa INSERT vào bảng notifications
    const record = body.record;
    if (!record || !record.user_id) {
       return new Response(JSON.stringify({ error: 'Không tìm thấy dữ liệu record hoặc user_id' }), { status: 400 });
    }

    const userId = record.user_id;
    // Map nội dung thông báo từ bảng notifications (bạn có thể tự chế lại chuỗi này)
    const title = 'TaskFlow Update'; 
    const message = `Có một cập nhật mới thuộc loại: ${record.type || 'Hệ thống'}`;
    const data = { reference_id: String(record.reference_id || '') };

    // 2. Lấy biến môi trường (Đã sửa tên cho đúng)
    const SUPABASE_URL = Deno.env.get('URL') || Deno.env.get('SUPABASE_URL'); // Lấy cái nào cũng được
    const SUPABASE_KEY = Deno.env.get('SERVICE_ROLE_KEY'); // ĐÃ SỬA TÊN Ở ĐÂY
    const SA_JSON = Deno.env.get('GOOGLE_SERVICE_ACCOUNT_JSON');

    if (!SUPABASE_URL || !SUPABASE_KEY || !SA_JSON) {
      console.error("Thiếu biến môi trường!");
      return new Response(JSON.stringify({ error: 'Missing configuration' }), { status: 500 });
    }

    const sa = JSON.parse(SA_JSON);

    // 3. Truy xuất Device Token từ bảng user_devices
    const url = `${SUPABASE_URL}/rest/v1/user_devices?user_id=eq.${encodeURIComponent(userId)}&select=device_token`;
    const tokensRes = await fetch(url, {
      method: 'GET',
      headers: {
        apikey: SUPABASE_KEY,
        Authorization: `Bearer ${SUPABASE_KEY}`
      }
    });
    
    if (!tokensRes.ok) {
      const text = await tokensRes.text();
      return new Response(JSON.stringify({ error: 'Failed to fetch device tokens', details: text }), { status: 502 });
    }
    
    const rows = await tokensRes.json();
    const tokens = rows.map((r: any) => r.device_token).filter(Boolean);
    
    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ ok: true, message: 'Người dùng này chưa có Device Token' }), { status: 200 });
    }

    // 4. Lấy Access Token từ Google
    const tokenResp = await fetchAccessToken(sa);
    const accessToken = tokenResp.access_token;
    if (!accessToken) throw new Error('No access_token returned from Google');

    // 5. Bắn thông báo qua Firebase
    const projectId = sa.project_id;
    const fcmBase = `https://fcm.googleapis.com/v1/projects/${projectId}`;

    const results: any[] = [];
    for (const token of tokens) {
      const payload = {
        message: {
          token: token,
          notification: { title, body: message },
          data: data,
          android: {
            priority: "high", // Tem hỏa tốc
            notification: {
              channel_id: "taskflow_push_channel_v4", // Phải y hệt trong Android Studio
              default_sound: true,
              default_vibrate_timings: true
            }
          }
        }
      };

      const res = await fetch(`${fcmBase}/messages:send`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json; charset=utf-8'
        },
        body: JSON.stringify(payload)
      });

      const text = await res.text();
      results.push({ token, status: res.status, response: text });
    }

    return new Response(JSON.stringify({ ok: true, sent: results }), { headers: { "Content-Type": "application/json" } });
  } catch (err) {
    console.error("Lỗi:", err);
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});