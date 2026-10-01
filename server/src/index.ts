import "dotenv/config";
import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import jwt from "jsonwebtoken";
import crypto from "node:crypto";
import twilio from "twilio";
import {Pool} from "pg";

const app=express();
app.disable("x-powered-by");
app.use(helmet());
app.use(cors({origin:process.env.CORS_ORIGIN?.split(",").map(s=>s.trim())??true}));
app.use(express.json({limit:"256kb"}));
app.use("/api/v1/auth/request-otp",rateLimit({windowMs:10*60*1000,max:5,standardHeaders:true,legacyHeaders:false}));
app.use("/api/v1/auth/verify-otp",rateLimit({windowMs:10*60*1000,max:10,standardHeaders:true,legacyHeaders:false}));

const secret=process.env.JWT_SECRET;
if(!secret||secret.length<32) throw new Error("JWT_SECRET must be at least 32 characters");
const pool=new Pool({connectionString:process.env.DATABASE_URL,ssl:process.env.DATABASE_SSL==="false"?false:{rejectUnauthorized:false}});
const jwtDays=Number(process.env.JWT_DAYS??30);

type Claims={sub:string,role:"user"|"admin",phone:string};
const auth=(req:express.Request):Claims|null=>{
 const h=req.header("authorization");
 if(!h?.startsWith("Bearer ")) return null;
 try{return jwt.verify(h.slice(7),secret) as Claims}catch{return null}
};
const requireRole=(role:"user"|"admin")=>(req:express.Request,res:express.Response,next:express.NextFunction)=>{
 const c=auth(req); if(!c)return res.status(401).json({error:"unauthorized"});
 if(c.role!==role)return res.status(403).json({error:"forbidden"});
 (req as any).claims=c; next();
};
const normalizePhone=(raw:string)=>{
 const p=String(raw??"").trim().replace(/[\s()-]/g,"");
 return /^\+[1-9]\d{7,14}$/.test(p)?p:null;
};
const hash=(v:string)=>crypto.createHash("sha256").update(v).digest("hex");
const randomCode=()=>crypto.randomInt(10000000,99999999).toString();

async function sendOtp(phone:string){
 const sid=process.env.TWILIO_ACCOUNT_SID,token=process.env.TWILIO_AUTH_TOKEN,service=process.env.TWILIO_VERIFY_SERVICE_SID;
 if(!sid||!token||!service)throw new Error("SMS provider is not configured");
 const client=twilio(sid,token);
 await client.verify.v2.services(service).verifications.create({to:phone,channel:"sms"});
}
async function ensureUser(phone:string,role:"user"|"admin"){
 const r=await pool.query("INSERT INTO users(phone,role) VALUES($1,$2) ON CONFLICT(phone) DO UPDATE SET updated_at=now() RETURNING id,phone,role",[phone,role]);
 return r.rows[0];
}
function tokenFor(u:any){return jwt.sign({sub:u.id,role:u.role,phone:u.phone},secret,{expiresIn:`${jwtDays}d`});}

app.get("/health",async(_req,res)=>{try{await pool.query("SELECT 1");res.json({ok:true,service:"specter-locate",time:new Date().toISOString()})}catch{res.status(503).json({ok:false})}});

app.post("/api/v1/auth/request-otp",async(req,res)=>{
 const phone=normalizePhone(req.body?.phone); const role=req.body?.role==="admin"?"admin":"user";
 if(!phone)return res.status(400).json({error:"invalid_phone",message:"Use E.164 format, e.g. +8801XXXXXXXXX"});
 if(role==="admin"){
   const exists=await pool.query("SELECT 1 FROM users WHERE phone=$1 AND role='admin'",[phone]);
   if(!exists.rowCount)return res.status(403).json({error:"admin_not_registered"});
 }
 try{
   await sendOtp(phone);
   await pool.query("INSERT INTO otp_requests(phone,role,expires_at) VALUES($1,$2,now()+interval '10 minutes')",[phone,role]);
   res.json({ok:true,expiresIn:600});
 }catch(e){console.error(e);res.status(503).json({error:"otp_unavailable"})}
});

app.post("/api/v1/auth/verify-otp",async(req,res)=>{
 const phone=normalizePhone(req.body?.phone),code=String(req.body?.code??"").trim(),role=req.body?.role==="admin"?"admin":"user";
 if(!phone||!/^[0-9]{4,10}$/.test(code))return res.status(400).json({error:"invalid_input"});
 const row=await pool.query("SELECT id FROM otp_requests WHERE phone=$1 AND role=$2 AND consumed=false AND expires_at>now() ORDER BY created_at DESC LIMIT 1",[phone,role]);
 if(!row.rowCount)return res.status(400).json({error:"otp_expired"});
 const sid=process.env.TWILIO_ACCOUNT_SID,token=process.env.TWILIO_AUTH_TOKEN,service=process.env.TWILIO_VERIFY_SERVICE_SID;
 if(!sid||!token||!service)return res.status(503).json({error:"otp_unavailable"});
 try{
   const check=await twilio(sid,token).verify.v2.services(service).verificationChecks.create({to:phone,code});
   if(check.status!=="approved")return res.status(401).json({error:"invalid_otp"});
   await pool.query("UPDATE otp_requests SET consumed=true WHERE id=$1",[row.rows[0].id]);
   const u=await ensureUser(phone,role);
   res.json({token:tokenFor(u),user:{id:u.id,phone:u.phone,role:u.role}});
 }catch{res.status(401).json({error:"invalid_otp"})}
});

app.post("/api/v1/auth/bootstrap-admin",async(req,res)=>{
 const bootstrap=String(req.body?.bootstrapCode??""); const phone=normalizePhone(req.body?.phone);
 if(!phone||!bootstrap||bootstrap!==process.env.ADMIN_BOOTSTRAP_CODE)return res.status(403).json({error:"invalid_bootstrap"});
 await pool.query("INSERT INTO users(phone,role) VALUES($1,'admin') ON CONFLICT(phone) DO UPDATE SET role='admin',updated_at=now()",[phone]);
 res.json({ok:true});
});

app.post("/api/v1/devices/register",requireRole("user"),async(req,res)=>{
 const c=(req as any).claims as Claims;
 const b=req.body??{};
 const name=String(b.deviceName??"Android device").trim().slice(0,80)||"Android device";
 const values=[c.sub,name,String(b.manufacturer??"").slice(0,80),String(b.model??"").slice(0,80),String(b.androidVersion??"").slice(0,40),String(b.appVersion??"1.0.0").slice(0,40)];
 const r=await pool.query(`INSERT INTO devices(user_id,device_name,manufacturer,model,android_version,app_version) VALUES($1,$2,$3,$4,$5,$6) RETURNING *`,values);
 res.json({device:r.rows[0]});
});

app.post("/api/v1/devices/heartbeat",requireRole("user"),async(req,res)=>{
 const c=(req as any).claims as Claims; const id=String(req.body?.deviceId??"");
 const battery=Number(req.body?.batteryPercent),charging=Boolean(req.body?.charging);
 if(!id)return res.status(400).json({error:"device_id_required"});
 const r=await pool.query("UPDATE devices SET online=true,last_seen_at=now(),battery_percent=$3,charging=$4,updated_at=now() WHERE id=$1 AND user_id=$2 RETURNING id",[id,c.sub,Number.isFinite(battery)?Math.max(0,Math.min(100,battery)):null,charging]);
 if(!r.rowCount)return res.status(404).json({error:"device_not_found"});
 res.json({ok:true,serverTime:new Date().toISOString()});
});

app.post("/api/v1/locations/batch",requireRole("user"),async(req,res)=>{
 const c=(req as any).claims as Claims; const deviceId=String(req.body?.deviceId??""); const points=req.body?.points;
 if(!deviceId||!Array.isArray(points)||points.length>100)return res.status(400).json({error:"invalid_batch"});
 const own=await pool.query("SELECT id FROM devices WHERE id=$1 AND user_id=$2",[deviceId,c.sub]); if(!own.rowCount)return res.status(404).json({error:"device_not_found"});
 const db=await pool.connect();
 try{
  await db.query("BEGIN");
  for(const p of points){
   const lat=Number(p.latitude),lon=Number(p.longitude),acc=Number(p.accuracy),ts=Number(p.capturedAt);
   if(!Number.isFinite(lat)||lat<-90||lat>90||!Number.isFinite(lon)||lon<-180||lon>180||!Number.isFinite(ts)||ts<0||ts>Date.now()+60000)throw new Error("invalid_location");
   await db.query("INSERT INTO location_points(device_id,latitude,longitude,accuracy,captured_at) VALUES($1,$2,$3,$4,to_timestamp($5/1000.0))",[deviceId,lat,lon,Number.isFinite(acc)?acc:null,ts]);
   await db.query("UPDATE devices SET online=true,last_seen_at=now(),last_latitude=$2,last_longitude=$3,last_accuracy=$4,last_location_at=to_timestamp($5/1000.0),updated_at=now() WHERE id=$1",[deviceId,lat,lon,Number.isFinite(acc)?acc:null,ts]);
  }
  await db.query("COMMIT");res.status(204).end();
 }catch(e){await db.query("ROLLBACK");res.status(400).json({error:"invalid_location"})}finally{db.release()}
});

app.get("/api/v1/devices/me",requireRole("user"),async(req,res)=>{
 const c=(req as any).claims as Claims; const r=await pool.query(`SELECT d.*, EXISTS(SELECT 1 FROM pairings p WHERE p.device_id=d.id AND p.claimed_at IS NOT NULL AND p.revoked_at IS NULL) AS paired, (SELECT p.admin_id FROM pairings p WHERE p.device_id=d.id AND p.claimed_at IS NOT NULL AND p.revoked_at IS NULL ORDER BY p.claimed_at DESC LIMIT 1) AS admin_id FROM devices d WHERE d.user_id=$1 ORDER BY d.created_at DESC`,[c.sub]);res.json({devices:r.rows});
});

app.post("/api/v1/pairings/code",requireRole("admin"),async(req,res)=>{
 const c=(req as any).claims as Claims; const code=randomCode(); const expires=Number(req.body?.expiresInSeconds??300); const safe=Math.max(60,Math.min(900,expires));
 await pool.query("INSERT INTO pairings(admin_id,code_hash,expires_at) VALUES($1,$2,now()+($3||' seconds')::interval)",[c.sub,hash(code),String(safe)]);
 res.json({code,expiresIn:safe});
});

app.post("/api/v1/pairings/claim",requireRole("user"),async(req,res)=>{
 const c=(req as any).claims as Claims; const code=String(req.body?.code??"").trim();
 if(!/^[0-9]{8}$/.test(code))return res.status(400).json({error:"invalid_code"});
 const device=await pool.query("SELECT id FROM devices WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1",[c.sub]); if(!device.rowCount)return res.status(404).json({error:"register_device_first"});
 const p=await pool.query("SELECT id,admin_id FROM pairings WHERE code_hash=$1 AND claimed_at IS NULL AND revoked_at IS NULL AND expires_at>now() ORDER BY created_at DESC LIMIT 1",[hash(code)]);
 if(!p.rowCount)return res.status(400).json({error:"invalid_or_expired_code"});
 await pool.query("UPDATE pairings SET device_id=$1,claimed_at=now() WHERE id=$2",[device.rows[0].id,p.rows[0].id]);
 res.json({ok:true,adminId:p.rows[0].admin_id,deviceId:device.rows[0].id});
});

app.get("/api/v1/admin/devices",requireRole("admin"),async(req,res)=>{
 const c=(req as any).claims as Claims;
 await pool.query("UPDATE devices SET online=false WHERE last_seen_at < now()-interval '90 seconds' AND id IN (SELECT device_id FROM pairings WHERE admin_id=$1 AND revoked_at IS NULL)",[c.sub]);
 const r=await pool.query(`SELECT d.*,p.admin_id FROM devices d JOIN pairings p ON p.device_id=d.id AND p.admin_id=$1 AND p.revoked_at IS NULL AND p.claimed_at IS NOT NULL ORDER BY d.updated_at DESC`,[c.sub]);
 res.json({devices:r.rows});
});

app.get("/api/v1/admin/devices/:id",requireRole("admin"),async(req,res)=>{
 const c=(req as any).claims as Claims; const id=req.params.id;
 const r=await pool.query(`SELECT d.* FROM devices d JOIN pairings p ON p.device_id=d.id WHERE d.id=$1 AND p.admin_id=$2 AND p.revoked_at IS NULL AND p.claimed_at IS NOT NULL LIMIT 1`,[id,c.sub]);
 if(!r.rowCount)return res.status(404).json({error:"device_not_found"});
 const history=await pool.query("SELECT latitude,longitude,accuracy,captured_at FROM location_points WHERE device_id=$1 ORDER BY captured_at DESC LIMIT 100",[id]);
 res.json({device:r.rows[0],history:history.rows});
});

app.post("/api/v1/admin/devices/:id/revoke",requireRole("admin"),async(req,res)=>{
 const c=(req as any).claims as Claims; const r=await pool.query("UPDATE pairings SET revoked_at=now() WHERE device_id=$1 AND admin_id=$2 AND revoked_at IS NULL",[req.params.id,c.sub]);
 if(!r.rowCount)return res.status(404).json({error:"pairing_not_found"});
 res.json({ok:true});
});

app.use((_req,res)=>res.status(404).json({error:"not_found"}));
app.listen(Number(process.env.PORT??3000),()=>console.log("SPECTER LOCATE API listening"));
