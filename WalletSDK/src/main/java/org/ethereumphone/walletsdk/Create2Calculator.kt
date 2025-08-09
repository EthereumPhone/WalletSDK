package org.ethereumphone.walletsdk

import com.esaulpaugh.headlong.abi.Tuple
import com.esaulpaugh.headlong.abi.TupleType
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.nio.ByteBuffer

class Create2Calculator {
    companion object {
        private const val FACTORY_ADDRESS = "0x0BA5ED0c6AA8c49038F819E587E2633c4A9F428a"
        private const val INIT_CODE_HASH = "0x5153041b4b8e36c84ca233b2fb610f85b8831b5e56a365618f507f8784fe034e"

        fun getAddress(owners: List<ByteArray>, nonce: BigInteger): String {
            // Calculate salt
            val salt = calculateSalt(owners, nonce)

            // Using lowercase factory address to match Solidity's address formatting
            val factoryAddr = FACTORY_ADDRESS.lowercase()

            // First 20 bytes of keccak256(0xff ++ deployingAddr ++ salt ++ keccak256(init_code))
            val ff = byteArrayOf(0xFF.toByte())
            val deployingAddr = Numeric.hexStringToByteArray(factoryAddr)
            val initCodeHashBytes = Numeric.hexStringToByteArray(INIT_CODE_HASH)

            val concatenated = ff + deployingAddr + salt + initCodeHashBytes

            val create2Hash = Hash.sha3(concatenated)
            val create2Address = create2Hash.slice(12..31).toByteArray() // Take last 20 bytes

            return Numeric.toHexString(create2Address)
        }

        private fun calculateSalt(owners: List<ByteArray>, nonce: BigInteger): ByteArray {
            // The format should match Solidity's abi.encode(bytes[], uint256)
            val tupleType: TupleType<Tuple> = TupleType.parse("(bytes[],uint256)")

            // Convert List<ByteArray> to Array<ByteArray>
            val ownersArray = owners.toTypedArray()

            // Create tuple and encode
            val tuple = Tuple.of(ownersArray, nonce)
            val encodedBuffer: ByteBuffer = tupleType.encode(tuple)

            // Debug log the encoded data
            val encodedArray = ByteArray(encodedBuffer.remaining())
            encodedBuffer.get(encodedArray)

            // Calculate and return salt
            return Hash.sha3(encodedArray)
        }
    }
}
