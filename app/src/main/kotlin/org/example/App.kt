package org.example
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.decodeFromString

// ./gradlew run

class FetchData(
    private val dataset: String,
    private val apiKey: String
) {
    fun fetch(): MutableList<Int> {
        val startTime = "2023-01-28T12:15:00"
        val endTime = "2023-06-28T12:30:00"
        val sortingOrder = "asc"
        val urlString = "https://data.fingrid.fi/api/datasets/$dataset/data" +
                "?startTime=${startTime}Z&endTime=${endTime}Z" +
                "&format=json&locale=fi&sortBy=startTime&sortOrder=$sortingOrder"

        val url = URI(urlString).toURL()
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val dataArray = jsonObject.getJSONArray("data")
                val values = mutableListOf<Int>() 
                for(i in 0 until dataArray.length()) {
                    val point = dataArray.getJSONObject(i)
                    val value = point.getInt("value")
                    values.add(value)
                }
                values
            } else {
                val error = "Error: $responseCode"
                println(error)
                mutableListOf()
            }
        } finally {
            connection.disconnect()
        }
    }
}

sealed class MergeValue {
    data class IntMap(val data: MutableMap<Int, Int>) : MergeValue()
    data class NestedMap(val data: MutableMap<String, MutableList<Int>>) : MergeValue()
}

suspend fun fetchElectricityData(): Map<String, MergeValue> {
    val api_Key = ""

    val electrictyConsumptionToday = FetchData(
        dataset = "124",
        apiKey = api_Key
    )

    val electrictyProductionToday = FetchData(
        dataset = "74",
        apiKey = api_Key
    )

    val windProduction = FetchData(
        dataset = "181",  
        apiKey = api_Key
    )

    val nuclearProduction = FetchData(
        dataset = "188",
        apiKey = api_Key
    )

    val waterProduction = FetchData(
        dataset = "191",
        apiKey = api_Key
    )

    val delayDuration = 2000L

    val electrictyConsumptionTodayResult = electrictyConsumptionToday.fetch()
    delay(delayDuration) 

    val electrictyProductionTodayResult = electrictyProductionToday.fetch()
    delay(delayDuration)

    val windProductionResult = windProduction.fetch()
    delay(delayDuration)

    val nuclearProductionTodayResult = nuclearProduction.fetch()
    delay(delayDuration)

    val waterProductionTodayResult = waterProduction.fetch()

    val production_v_consumption: MutableMap<Int, Int> = electrictyProductionTodayResult.zip(electrictyConsumptionTodayResult).toMap().toMutableMap()
    val productions: MutableMap<String, MutableList<Int>> = mutableMapOf()
    val merge: MutableMap<String, MergeValue> = mutableMapOf()  

    productions.getOrPut("Vesivoima") { mutableListOf() }.addAll(waterProductionTodayResult)
    productions.getOrPut("Ydinvoima") { mutableListOf() }.addAll(nuclearProductionTodayResult)
    productions.getOrPut("Tuulivoima") { mutableListOf() }.addAll(windProductionResult)

    merge["tuotanto_v_kulutus"] = MergeValue.IntMap(production_v_consumption)
    merge["tuotanto"] = MergeValue.NestedMap(productions)
    return merge
}


@Serializable
data class Note(
    val id: Int,
    val body: String
)

fun main() = runBlocking {
    val result = fetchElectricityData()

    val production = result["tuotanto"]
    if(production is MergeValue.NestedMap) {
        val data  = production.data
        println("Tuotanto data: $data")
    }
 
     val supabase = createSupabaseClient(
        supabaseUrl = "",
        supabaseKey = ""
    ) {
        install(Postgrest)
    } 

    val response = supabase.postgrest.from("notes").select()

     println("Data: ${response.data}")
}


