package com.specter.locate.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="location_points")
data class LocationPoint(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val latitude:Double,
 val longitude:Double,
 val accuracy:Float,
 val capturedAt:Long=System.currentTimeMillis()
)