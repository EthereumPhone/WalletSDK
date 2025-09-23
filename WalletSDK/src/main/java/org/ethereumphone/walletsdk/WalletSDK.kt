package org.ethereumphone.walletsdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Base64
import com.esaulpaugh.headlong.abi.Tuple
import com.esaulpaugh.headlong.abi.TupleType
import com.esaulpaugh.headlong.abi.TypeFactory
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ethereumphone.walletsdk.model.NoSysWalletException
import org.json.JSONObject
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint192
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.ContractUtils
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.interfaces.ECPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume
import org.web3j.abi.datatypes.DynamicStruct


@SuppressLint("WrongConstant")
class WalletSDK(
    private val context: Context,
    private var web3jInstance: Web3j = Web3j.build(HttpService("https://rpc.ankr.com/eth")),
    private val factoryAddress: String = "0x0BA5ED0c6AA8c49038F819E587E2633c4A9F428a",
    private val entryPointAddress: String = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789",
    private val bundlerRPCUrl: String
) {
    // System service methods using reflection
    private val cls: Class<*> = Class.forName(SYS_SERVICE_CLASS)
    private val createSession = cls.declaredMethods.first { it.name == "createSession" }
    private val sendTransaction = cls.declaredMethods.first { it.name == "sendTransaction" }
    private val signMessage = cls.declaredMethods.first { it.name == "signMessage" }
    private val switchAccount = cls.declaredMethods.first { it.name == "switchAccount" }
    private val getAddress = cls.declaredMethods.first { it.name == "getAddress" }
    private val getChainId = cls.declaredMethods.first { it.name == "getChainId" }
    private val changeChainId = cls.declaredMethods.first { it.name == "changeChainId" }
    private val isWalletConnected = cls.declaredMethods.first { it.name == "isWalletConnected" }

    private val getInitCode = cls.declaredMethods.first { it.name == "getInitCode" }

    private var address: String? = null
    private var initCodeFromOS: String? = null
    private var proxy: Any? = null
    private lateinit var session: String
    private var bundlerRPC = bundlerRPCUrl

    init {
        proxy = initializeProxyService()
        session = createSession.invoke(proxy) as String

        CoroutineScope(Dispatchers.IO).launch {
            address = getAddress()
        }
    }

    private fun initializeProxyService() =
        context.getSystemService(SYS_SERVICE) ?: throw NoSysWalletException("System wallet not found on this device")

    suspend fun getAddress(): String = suspendCancellableCoroutine { continuation ->
        address?.let {
            continuation.resume(it)
            return@suspendCancellableCoroutine
        }
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val result = resultData?.getString("result")
                if (result != null) {
                    address = result
                    continuation.resume(result)
                } else {
                    continuation.resume("")
                }
            }
        }

        getAddress.invoke(proxy, session, receiver)
    }

    private fun encodePacked(items: List<DynamicBytes>): ByteArray {
        val result = ByteArrayOutputStream()

        // Write length of array
        result.write(encodeInt(items.size.toBigInteger()))

        // Write each item
        items.forEach { bytes ->
            // Write length of bytes
            result.write(encodeInt(bytes.value.size.toBigInteger()))
            // Write bytes
            result.write(bytes.value)
        }

        return result.toByteArray()
    }

    private fun encodeInt(value: BigInteger): ByteArray {
        return Numeric.toBytesPadded(value, 32)
    }

    suspend fun getPair(): Pair<BigInteger, BigInteger>? = suspendCancellableCoroutine { continuation ->
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val result = resultData?.getString("result")
                if (result != null) {
                    try {
                        val realAddress = decodeECPublicKey(result)
                        continuation.resume(getPublicKeyCoordinates(realAddress))
                    } catch (
                        e: Exception
                    ) {
                        e.printStackTrace()
                        continuation.resume(null)
                    }

                } else {
                    continuation.resume(null)
                }
            }
        }

        getAddress.invoke(proxy, session, receiver)
    }

    /**
     * Helper functions
     */
    fun getPublicKeyCoordinates(publicKey: ECPublicKey): Pair<BigInteger, BigInteger> {
        val point = publicKey.w
        return Pair(
            point.affineX,
            point.affineY
        )
    }

    fun encodeInitCode(
        factoryAddress: String = "0x0BA5ED0c6AA8c49038F819E587E2633c4A9F428a",
        owners: List<ByteArray>,
        nonce: BigInteger
    ): String {
        val function = com.esaulpaugh.headlong.abi.Function.parse("createAccount(bytes[],uint256)", "(address)")
        val encodedFunction = function.encodeCall(Tuple.of(owners.toTypedArray(), nonce))

        val concat = Numeric.hexStringToByteArray(factoryAddress) + encodedFunction.array()
        return Numeric.toHexString(concat)
    }


    /**
     * Get the address of a public key using the precompiled contract
     */
    suspend fun getPrecomputedAddress(
        pubKeyX: BigInteger,
        pubKeyY: BigInteger,
        salt: BigInteger = BigInteger.ZERO
    ): String = withContext(Dispatchers.IO) {
        val owner = encodeSignatureData(pubKeyX, pubKeyY)
        val function = Function(
            "getAddress",
            listOf(
                DynamicArray(DynamicBytes::class.java, DynamicBytes(owner)),
                Uint256(salt)
            ),
            listOf(TypeReference.create(org.web3j.abi.datatypes.Address::class.java))
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val response = try {
            web3jInstance.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    factoryAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).sendAsync().get()
        } catch (e: Exception) {
            throw Exception("Error calling getAddress: ${e.message}")
        }
        if (response.hasError()) {
            throw Exception("Error calling getAddress: ${response.error.message}")
        }
        return@withContext org.web3j.abi.FunctionReturnDecoder.decode(
            response.value,
            function.outputParameters
        )[0].value as String
    }

    /**
     * Function to abi.encode the r,s into a SignatureData bytearray
     */
    fun encodeSignatureData(r: BigInteger, s: BigInteger): ByteArray {
        // Define the struct type
        val structType = TupleType.parse<Tuple>("(uint256,uint256)")

        // Create the tuple with our values
        val signatureData = Tuple.of(r, s)

        // Encode the tuple
        val encoded = structType.encode(signatureData)

        // Convert the ByteBuffer to ByteArray
        return encoded.array()
    }

    fun decodeECPublicKey(encodedKey: String?): ECPublicKey {
        try {
            // Decode Base64 string to byte array
            val decodedKey: ByteArray = Base64.decode(encodedKey, Base64.NO_WRAP)


            // Create X509EncodedKeySpec from decoded bytes
            val keySpec = X509EncodedKeySpec(decodedKey)


            // Get KeyFactory for EC algorithm
            val keyFactory: KeyFactory = KeyFactory.getInstance("EC")


            // Generate public key from spec
            return keyFactory.generatePublic(keySpec) as ECPublicKey
        } catch (e: NoSuchAlgorithmException) {
            throw e
        } catch (e: InvalidKeySpecException) {
            throw e
        } catch (e: java.lang.Exception) {
            throw RuntimeException("Error decoding public key", e)
        }
    }

    data class TxParams (
        val to: String,
        val value: String,
        val data: String
    )

    /**
     * Internal representation of the Solidity `Call` struct used by `executeBatch`.
     * struct Call {
     *   address target;
     *   uint256 value;
     *   bytes   data;
     * }
     */
    data class CallStruct(
        val target: Address,
        val value: Uint256,
        val data: DynamicBytes
    ) : DynamicStruct(target, value, data)

    suspend fun sendTransaction(
        to: String,
        value: String,
        data: String,
        callGas: BigInteger?,
        chainId: Int? = null,
        rpcEndpoint: String? = null,
        gasProvider: (suspend (UserOperation) -> GasEstimation)? = null,
    ): String {
        return sendTransaction(
            listOf(TxParams(to, value, data)),
            callGas,
            chainId,
            rpcEndpoint,
            gasProvider = gasProvider
        )
    }

    /**
     * Send a batch transaction using the UserOperation model
     * @param txParamsList List of transactions to execute
     * @param callGas Gas limit for the call
     * @param chainId Chain ID for the transaction
     * @param rpcEndpoint RPC endpoint to use (optional)
     * @param bundlerRPC Bundler RPC endpoint to use
     * @param submitUserOpCustom Custom function to submit UserOperation
     * @param gasProvider Custom function to provide gas estimation
     * @return Transaction hash or error message
     */
    suspend fun sendTransaction(
        txParamsList: List<TxParams>,
        callGas: BigInteger?,
        chainId: Int? = null,
        rpcEndpoint: String? = null,
        bundlerRPC: String = this.bundlerRPC,
        submitUserOpCustom: ((UserOperation) -> String)? = null,
        gasProvider: (suspend (UserOperation) -> GasEstimation)? = null,
    ): String {
        val from = getAddress()
        rpcEndpoint?.let {
            web3jInstance = Web3j.build(HttpService(it))
        }
        return suspendCancellableCoroutine { continuation ->
            try {
                // Build the list of Call structs expected by `executeBatch`
                val calls = txParamsList.map { tx ->
                    CallStruct(
                        Address(tx.to),
                        Uint256(BigInteger(tx.value)),
                        DynamicBytes(Numeric.hexStringToByteArray(tx.data))
                    )
                }

                val function = org.web3j.abi.datatypes.Function(
                    "executeBatch", // function name
                    listOf(
                        DynamicArray(CallStruct::class.java, calls)
                    ),
                    emptyList() // output parameters
                )

                val callData = FunctionEncoder.encode(function)

                val nonceForUserOp = runBlocking { getNonce(from) }

                val gasPrices = getGasPrice(bundlerRPCUrl = bundlerRPC)

                var initCode = ""

                if (!isDeployed(from)) {
                    initCode = runBlocking { getInitCode() }
                }

                // Create a UserOperation with zero gas limits for estimation
                val estimationUserOp = UserOperation(
                    sender = from,
                    nonce = nonceForUserOp,
                    initCode = initCode,
                    callData = callData,
                    callGasLimit = BigInteger.ZERO,
                    verificationGasLimit = BigInteger.ZERO,
                    preVerificationGas = BigInteger.ZERO,
                    maxFeePerGas = gasPrices.maxFeePerGas,
                    maxPriorityFeePerGas = gasPrices.maxPriorityFeePerGas,
                    paymasterAndData = "",
                    // Dummy signature for estimation
                    signature = "0x0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000001c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000001200000000000000000000000000000000000000000000000000000000000000017000000000000000000000000000000000000000000000000000000000000000190dcd684fb36d42ddd1f70dd6bca4c32be4306525e3bdeacd1dc597a1ad9c2087a92090fb86fa25180b288520c593cbad2d38893e52e17142375fd4885b5cca600000000000000000000000000000000000000000000000000000000000000210000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000517b2274797065223a22776562617574686e2e676574222c226368616c6c656e6765223a223170776c4c6f74743651515549614e4e7779505f41507058514641397153776c4e4d34673944365a777959227d000000000000000000000000000000"
                )

                // Estimate gas limits using custom provider or default method
                val gasEstimation = runBlocking {
                    if (gasProvider != null) {
                        gasProvider(estimationUserOp)
                    } else {
                        estimateUserOperationGas(estimationUserOp)
                    }
                }

                // Create the UserOp with estimated gas limits
                val userOp = UserOperation(
                    sender = from,
                    nonce = nonceForUserOp,
                    initCode = initCode,
                    callData = callData,
                    callGasLimit = callGas ?: gasEstimation.callGasLimit,
                    verificationGasLimit = gasEstimation.verificationGasLimit.multiply(BigInteger("2")),
                    preVerificationGas = gasEstimation.preVerificationGas.multiply(BigInteger("2")),
                    maxFeePerGas = gasPrices.maxFeePerGas,
                    maxPriorityFeePerGas = gasPrices.maxPriorityFeePerGas,
                    paymasterAndData = "",
                    // Signature gets filled in later
                    signature = ""
                )

                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        val result = resultData?.getString("result")
                        if (result == DECLINE) {
                            continuation.resume(DECLINE)
                        } else if (result != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                println("Signature: $result")
                                val signedUserOp = Gson().fromJson(result, UserOperation::class.java)
                                val out = if(submitUserOpCustom != null) {
                                    submitUserOpCustom(signedUserOp)
                                } else {
                                    sendUserOpToBundler(signedUserOp)
                                }
                                // {"jsonrpc":"2.0","id":1,"result":"User OP hash"}
                                try {
                                    val jsonObject = JSONObject(out)
                                    if (jsonObject.has("result")) {
                                        continuation.resume(jsonObject.getString("result"))
                                    } else {
                                        continuation.resume("Error: ${jsonObject.getString("error")}")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    continuation.resume("Error: ${e.message}")
                                }
                            }
                        } else {
                            continuation.resume("")
                        }
                    }
                }

                val finalUnsignedUserOp = Gson().toJson(userOp)

                println("finalUnsignedUserOp: $finalUnsignedUserOp")

                sendTransaction.invoke(
                    proxy,
                    session,
                    finalUnsignedUserOp,
                    chainId,
                    receiver
                )
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume("Error: ${e.message}")
            }
        }
    }

    /**
     * Estimate gas for a user operation using the bundler RPC
     */
    suspend fun estimateUserOperationGas(userOp: UserOperation): GasEstimation = withContext(Dispatchers.IO) {
        val client = OkHttpClient()

        // Create the UserOperation JSON object
        val userOpJson = JsonObject().apply {
            addProperty("sender", userOp.sender)
            addProperty("nonce", "0x" + userOp.nonce.toString(16))
            addProperty("initCode", if (userOp.initCode.isEmpty()) "0x" else userOp.initCode)
            addProperty("callData", if (userOp.callData.isEmpty()) "0x" else userOp.callData)
            addProperty("callGasLimit", "0x" + userOp.callGasLimit.toString(16))
            addProperty("verificationGasLimit", "0x" + userOp.verificationGasLimit.toString(16))
            addProperty("preVerificationGas", "0x" + userOp.preVerificationGas.toString(16))
            addProperty("maxFeePerGas", "0x" + userOp.maxFeePerGas.toString(16))
            addProperty("maxPriorityFeePerGas", "0x" + userOp.maxPriorityFeePerGas.toString(16))
            addProperty("paymasterAndData", if (userOp.paymasterAndData.isEmpty()) "0x" else userOp.paymasterAndData)
            addProperty("signature", userOp.signature)
        }

        // Create the params array
        val paramsArray = JsonArray().apply {
            add(userOpJson)
            add(entryPointAddress) // EntryPoint contract address
        }

        // Create the complete RPC request
        val rpcRequest = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "eth_estimateUserOperationGas")
            add("params", paramsArray)
            addProperty("id", 1)
        }

        // Create and execute the HTTP request
        val request = Request.Builder()
            .url(bundlerRPC)
            .post(rpcRequest.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to estimate UserOperation gas: ${response.body?.string()}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response from bundler")
            val jsonResponse = Gson().fromJson(responseBody, JsonObject::class.java)

            if (jsonResponse.has("error")) {
                val error = jsonResponse.getAsJsonObject("error")
                return@withContext GasEstimation(
                    preVerificationGas = BigInteger("100000"),
                    verificationGasLimit = BigInteger("500000"),
                    callGasLimit = BigInteger("500000")
                )
            }

            val result = jsonResponse.getAsJsonObject("result")

            return@withContext GasEstimation(
                preVerificationGas = BigInteger(result.get("preVerificationGas").asString.substring(2), 16),
                verificationGasLimit = BigInteger(result.get("verificationGasLimit").asString.substring(2), 16),
                callGasLimit = BigInteger(result.get("callGasLimit").asString.substring(2), 16)
            )
        }
    }

    private suspend fun getInitCode(): String = suspendCancellableCoroutine { continuation ->
        initCodeFromOS?.let {
            continuation.resume(it)
            return@suspendCancellableCoroutine
        }
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val result = resultData?.getString("result")
                if (result != null) {
                    initCodeFromOS = result
                    continuation.resume(result)
                } else {
                    continuation.resume("")
                }
            }
        }
        getInitCode.invoke(proxy, session, receiver)
    }

    fun isDeployed(address: String): Boolean {
        val code = web3jInstance.ethGetCode(address, DefaultBlockParameterName.LATEST).send()

        return code.code.length > 2
    }

    fun sendUserOpToBundler(
        userOp: UserOperation
    ): String {
        val client = OkHttpClient()

        // Create the UserOperation JSON object
        val userOpJson = JsonObject().apply {
            addProperty("sender", userOp.sender)
            addProperty("nonce", "0x" + userOp.nonce.toString(16))
            addProperty("initCode", if (userOp.initCode.isEmpty()) "0x" else userOp.initCode)
            addProperty("callData", if (userOp.callData.isEmpty()) "0x" else userOp.callData)
            addProperty("callGasLimit", "0x" + userOp.callGasLimit.toString(16))
            addProperty("verificationGasLimit", "0x" + userOp.verificationGasLimit.toString(16))
            addProperty("preVerificationGas", "0x" + userOp.preVerificationGas.toString(16))
            addProperty("maxFeePerGas", "0x" + userOp.maxFeePerGas.toString(16))
            addProperty("maxPriorityFeePerGas", "0x" + userOp.maxPriorityFeePerGas.toString(16))
            addProperty("paymasterAndData", if (userOp.paymasterAndData.isEmpty()) "0x" else userOp.paymasterAndData)
            addProperty("signature", userOp.signature)
        }

        // Create the params array
        val paramsArray = JsonArray().apply {
            add(userOpJson)
            add(entryPointAddress) // EntryPoint contract address
        }

        // Create the complete RPC request
        val rpcRequest = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "eth_sendUserOperation")
            add("params", paramsArray)
            addProperty("id", 1)
        }

        // Create and execute the HTTP request
        val request = Request.Builder()
            .url(bundlerRPC)
            .post(rpcRequest.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to send UserOperation: ${response.body?.string()}")
            }
            // Return the response body which will contain the userOpHash
            return response.body?.string() ?: ""
        }
    }

    fun getGasPrice(bundlerRPCUrl: String): GasPrice {
        val client = OkHttpClient()

        val rpcRequest = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "pimlico_getUserOperationGasPrice")
            add("params", JsonArray())
            addProperty("id", 1)
        }

        val request = Request.Builder()
            .url(bundlerRPCUrl)
            .post(rpcRequest.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to get gas price: ${response.body?.string()}")
            }

            val responseBody = response.body?.string()
            val jsonResponse = Gson().fromJson(responseBody, JsonObject::class.java)
            val fast = jsonResponse.getAsJsonObject("result").getAsJsonObject("fast")

            return GasPrice(
                maxFeePerGas = BigInteger(fast.get("maxFeePerGas").asString.substring(2), 16),
                maxPriorityFeePerGas = BigInteger(fast.get("maxPriorityFeePerGas").asString.substring(2), 16)
            )
        }
    }

    /**
     * Function to get correct nonce for UserOp
     */
    suspend fun getNonce(
        senderAddress: String,
        rpcEndpoint: String? = null
    ): BigInteger = withContext(Dispatchers.IO) {
        rpcEndpoint?.let {
            web3jInstance = Web3j.build(HttpService(it))
        }
        val function = Function(
            "getNonce",
            listOf(
                Address(senderAddress),
                Uint192(BigInteger.ZERO) // key = 0
            ),
            listOf(TypeReference.create(Uint256::class.java))
        )

        val encodedFunction = FunctionEncoder.encode(function)
        val transaction = Transaction.createEthCallTransaction(
            senderAddress,
            entryPointAddress,
            encodedFunction
        )

        val response = web3jInstance.ethCall(
            transaction,
            DefaultBlockParameterName.LATEST
        ).send()

        if (response.hasError()) {
            throw Exception("Error getting nonce: ${response.error.message}")
        }
        BigInteger(response.value.substring(2), 16)  // Added radix parameter 16 for hex
    }

    suspend fun signMessage(message: String, chainId: Int, type: String = "personal_sign"): String {
        val from = getAddress()

        return suspendCancellableCoroutine { continuation ->

            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val result = resultData?.getString("result")
                    continuation.resume(result ?: "")
                }
            }

            signMessage.invoke(proxy, session, message, chainId.toString(), from, type, receiver)
        }
    }

    suspend fun getChainId(): Int = suspendCancellableCoroutine { continuation ->
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val result = resultData?.getString("result")
                continuation.resume(result?.toIntOrNull() ?: 1)
            }
        }

        getChainId.invoke(proxy, session, receiver)
    }

    suspend fun changeChain(chainId: Int, rpcEndpoint: String, mBundlerRPCUrl: String): String =
        suspendCancellableCoroutine { continuation ->
            web3jInstance = Web3j.build(HttpService(rpcEndpoint))
            bundlerRPC = mBundlerRPCUrl

            println("Updated web3jInstance to $rpcEndpoint")

            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val result = resultData?.getString("result")
                    continuation.resume(result ?: "")
                }
            }

            changeChainId.invoke(proxy, session, chainId, receiver)
        }

    suspend fun switchAccount(index: Int): String = suspendCancellableCoroutine { continuation ->
        var isCompleted = false

        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (!isCompleted) {
                    isCompleted = true
                    val result = resultData?.getString("result")
                    continuation.resume(result ?: "")
                }
            }
        }

        continuation.invokeOnCancellation {
            if (!isCompleted) {
                isCompleted = true
            }
        }

        try {
            switchAccount.invoke(proxy, session, index, receiver)
        } catch (e: Exception) {
            if (!isCompleted) {
                isCompleted = true
                continuation.resume("") // Or handle error case differently
            }
        }
    }

    fun isWalletConnected(): Boolean {
        return try {
            isWalletConnected.invoke(proxy, session) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun isEthOS(): Boolean {
        return proxy != null
    }

    companion object {
        const val SYS_SERVICE_CLASS = "android.os.WalletProxy"
        const val SYS_SERVICE = "wallet"
        const val DECLINE = "decline"
    }

    data class UserOperation(
        val sender: String,
        val nonce: BigInteger,
        val initCode: String,
        val callData: String,
        val callGasLimit: BigInteger,
        val verificationGasLimit: BigInteger,
        val preVerificationGas: BigInteger,
        val maxFeePerGas: BigInteger,
        val maxPriorityFeePerGas: BigInteger,
        val paymasterAndData: String,
        var signature: String
    )

    data class GasPrice(
        val maxFeePerGas: BigInteger,
        val maxPriorityFeePerGas: BigInteger
    )

    data class GasEstimation(
        val preVerificationGas: BigInteger,
        val verificationGasLimit: BigInteger,
        val callGasLimit: BigInteger
    )
}