package pl.sprytneDzbany.kettleApp

import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity

object PermissionManger: AppCompatActivity() {

    class Permission(
        val name: String,
        val code: Int
    ){}

    private const val TAG = "permission"
    private val permissionList: MutableList<Permission> = ArrayList()
    private var context: AppCompatActivity = this

    fun setContext(c: AppCompatActivity){
        context = c
    }

    fun addPermission(permissionName: String, code: Int)
    {
        permissionList.add(Permission(permissionName, code))
    }

    fun setupPermissions(){
        permissionList.forEach { permission ->
            val isPermission = ContextCompat.checkSelfPermission(context, permission.name)

            if (isPermission != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Requesting for permission ${permission.name}...")
                ActivityCompat.requestPermissions(context, arrayOf(permission.name), permission.code)
            }
        }
    }

    fun checkPermissions(): Boolean {
        var result = true
        permissionList.forEach{ permission ->
            val isPermission = ContextCompat.checkSelfPermission(context, permission.name)
            if (isPermission != PackageManager.PERMISSION_GRANTED) {result = false}
        }
        return result
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionList.forEach{ permission ->
            val name = permission.name
            val code = permission.code
            if(requestCode == code){
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission $name has been denied by user")
                } else {
                    Log.i(TAG, "Permission $name has been granted by user")
                }
            }
        }
    }
}