package com.specter.locate.data
import android.content.Context
import androidx.room.*
@Database(entities=[LocationPoint::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){
 abstract fun locationDao():LocationDao
 companion object {
  @Volatile private var INSTANCE:AppDatabase?=null
  fun get(context:Context):AppDatabase=INSTANCE?:synchronized(this){
   INSTANCE?:Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"specter_locate.db").build().also{INSTANCE=it}
  }
 }
}