import "dotenv/config";
import express from "express";
import cors from "cors";
import jwt from "jsonwebtoken";
import {Pool} from "pg";
const app=express(); app.use(cors()); app.use(express.json({limit:"1mb"}));
const secret=process.env.JWT_SECRET; if(!secret) throw new Error("JWT_SECRET is required");
const pool=process.env.DATABASE_URL?new Pool({connectionString:process.env.DATABASE_URL}):null;
function auth(req:express.Request){const h=req.header("authorization");if(!h?.startsWith("Bearer "))return null;try{return jwt.verify(h.slice(7),secret) as {sub:string}}catch{return null}}
app.get("/health",(_req,res)=>res.json({ok:true,service:"specter-locate"}));
app.post("/api/v1/auth/demo",(req,res)=>{const userId=String(req.body?.userId??"").trim();if(!userId||userId.length>128)return res.status(400).json({error:"invalid_user_id"});return res.json({token:jwt.sign({sub:userId},secret,{expiresIn:"30d"})})});
app.post("/api/v1/locations/batch",async(req,res)=>{const claims=auth(req);if(!claims)return res.status(401).json({error:"unauthorized"});if(!pool)return res.status(503).json({error:"database_not_configured"});const points=req.body?.points;if(!Array.isArray(points)||points.length>500)return res.status(400).json({error:"invalid_batch"});const c=await pool.connect();try{await c.query("BEGIN");for(const p of points){const lat=Number(p.latitude),lon=Number(p.longitude),acc=Number(p.accuracy),ts=Number(p.capturedAt);if(!Number.isFinite(lat)||lat<-90||lat>90||!Number.isFinite(lon)||lon<-180||lon>180||!Number.isFinite(ts)){await c.query("ROLLBACK");return res.status(400).json({error:"invalid_location"})}await c.query("INSERT INTO location_points(user_id,latitude,longitude,accuracy,captured_at) VALUES($1,$2,$3,$4,to_timestamp($5/1000.0))",[claims.sub,lat,lon,Number.isFinite(acc)?acc:null,ts])}await c.query("COMMIT");return res.status(204).end()}catch(e){await c.query("ROLLBACK");console.error(e);return res.status(500).json({error:"storage_failed"})}finally{c.release()}});
app.listen(Number(process.env.PORT??3000),()=>console.log("SPECTER LOCATE API listening"));