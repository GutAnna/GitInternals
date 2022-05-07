package gitinternals

import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.InflaterInputStream

const val FILECODE = "100644"

fun decompress(filePath: String): InflaterInputStream = InflaterInputStream(FileInputStream(filePath))

fun getTimestamp(seconds: Long, zone: String): String = Instant.ofEpochSecond(seconds).atZone(ZoneOffset.of(zone))
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx"))

fun printCommitData(data: List<String>) {
    var i = 0
    val tree = data[i].substringAfter("tree ")
    print("tree: $tree")
    i++
    if (data[i].contains("parent")) {
        val parent = data[i].substringAfter("parent ")
        print("\nparents: $parent")
        i++
    }
    if (data[i].contains("parent")) {
        val parent = data[i].substringAfter("parent ")
        print(" | $parent")
        i++
    }
    val author = data[i].split(" ")
    var time = getTimestamp(author[3].toLong(), author[4])
    println("\nauthor: ${author[1]} ${author[2].trim('<', '>')} original timestamp: $time")
    i++
    val committer = data[i].split(" ")
    time = getTimestamp(committer[3].toLong(), committer[4])
    println("committer: ${committer[1]} ${committer[2].trim('<', '>')} commit timestamp: $time")
    i++
    println("commit message:")
    for (j in i + 1 until data.size - 1) {
        println(data[j])
    }
}

fun printTreeData(data: String) {
    val lines = data.split(0.toChar())
    var (number, name) = lines[1].split(" ")
    var hash = ""
    for (i in 2 until lines.size - 1) {
        hash = lines[i].substring(0, 20).map { it.code.toByte() }.joinToString("") { "%02x".format(it)}
        val tmp = lines[i].substring(20).split(" ")
        println("$number $hash $name")
        number = tmp[0]
        name = tmp[1]
    }
    hash = lines[lines.size - 1].map { it.code.toByte() }.joinToString("") { "%02x".format(it)}
    println("$number $hash $name")
}

fun aboutCatFile(directory: String) {
    println("Enter git object hash:")
    val hashObject = readLine()!!
    val content = decompress("$directory/objects/${hashObject.substring(0, 2)}/${hashObject.substring(2)}").readAllBytes()
    val contentList = content.toString(Charsets.UTF_8).split("\n")

    if (contentList[0].contains("blob")) {
        println("*BLOB*")
        println(content.toString(Charsets.UTF_8).split(0.toChar())[1])
    } else if (contentList[0].contains("commit")) {
        println("*COMMIT*")
        printCommitData(contentList)
    } else if (contentList[0].contains("tree")) {
        println("*TREE*")
        printTreeData(content.map { Char(it.toUShort()) }.joinToString("") )
    }
}
fun getListBranches(directory: String) {
    val listBranches = File("$directory/refs/heads").listFiles()
    val activeBranch = File("$directory/HEAD").readText().substringAfterLast("/")
    listBranches.forEach { if (activeBranch.contains(it.name)) println("* ${it.name}") else println("  ${it.name}") }
}

fun readCommit(directory: String, hashObject: String, merged: String = "") {
    val content = decompress("$directory/objects/${hashObject.substring(0, 2)}/${hashObject.substring(2)}").readAllBytes()
    val data = content.toString(Charsets.UTF_8).split("\n")
    var i = 1
    var parent = ""
    var parentMerged = ""
    println("Commit: $hashObject$merged")
    if (data[i].contains("parent")) {
        parent = data[i].substringAfter("parent ")
        i++
    }
    if (data[i].contains("parent")) {
        parentMerged = data[i].substringAfter("parent ")
        i++
    }
    i++
    val committer = data[i].split(" ")
    var time = getTimestamp(committer[3].toLong(), committer[4])
    println("${committer[1]} ${committer[2].trim('<', '>')} commit timestamp: $time")
    i++

    for (j in i + 1 until data.size - 1) {
        println(data[j])
    }
    println()
    if (merged.isEmpty()) {
        if (parentMerged.isNotEmpty()) readCommit(directory, parentMerged, " (merged)")
        if (parent.isNotEmpty()) readCommit(directory, parent)
    }
}

fun printLog(directory: String) {
    println("Enter branch name:")
    val branchName = readLine()!!
    val commitObject = File("$directory/refs/heads/$branchName")
    if (commitObject.exists()) readCommit(directory,commitObject.readText().removeSuffix("\n"))
}

fun printFileName(directory: String, hashTree: String, folder: String = "") {
    val dataTree = decompress("$directory/objects/${hashTree.substring(0, 2)}/${hashTree.substring(2)}").readAllBytes()
    val data = dataTree.map { Char(it.toUShort()) }.joinToString("")
    val lines = data.split(0.toChar())
    var (number, name) = lines[1].split(" ")
    var hash = ""
    for (i in 2 until lines.size - 1) {
        hash = lines[i].substring(0, 20).map { it.code.toByte() }.joinToString("") { "%02x".format(it)}
        val tmp = lines[i].substring(20).split(" ")
        if (number == FILECODE) println("$folder$name") else printFileName(directory,hash,"$name/" )
        number = tmp[0]
        name = tmp[1]
    }
    hash = lines[lines.size - 1].map { it.code.toByte() }.joinToString("") { "%02x".format(it)}
    if (number == FILECODE) println("$folder$name") else printFileName(directory,hash,"$name/" )
}

fun printTreeList(directory: String) {
    println("Enter commit-hash:")
    val hashObject = readLine()!!
    val content = decompress("$directory/objects/${hashObject.substring(0, 2)}/${hashObject.substring(2)}").readAllBytes()
    val data = content.toString(Charsets.UTF_8).split("\n")
    if (data[0].contains("tree")) {
        val hashTree = data[0].split(" ").last()
        printFileName(directory,hashTree)
    }
}

fun main() {
    println("Enter .git directory location:")
    val directory = readLine()!!
    println("Enter command:")
    when(readLine()!!) {
        "cat-file" -> aboutCatFile(directory)
        "list-branches" -> getListBranches(directory)
        "log" -> printLog(directory)
        "commit-tree" -> printTreeList(directory)
        else -> print("Unknown command")
    }
}