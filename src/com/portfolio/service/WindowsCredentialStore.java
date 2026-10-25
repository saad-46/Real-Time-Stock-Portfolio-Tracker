package com.portfolio.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows Credential Manager through advapi32 (CredWrite / CredRead / CredDelete), called from a short-lived PowerShell process.
 * No extra library is needed. The secret travels only on that process's standard input and (for a read) its standard output:
 * never on a command line, in a file, or in a log. Entries are generic credentials kept per Windows user.
 */
public final class WindowsCredentialStore implements CredentialStore {
    private static final long TIMEOUT_SECONDS = 20;
    private static final int EXIT_OK = 0;
    private static final int EXIT_NOT_FOUND = 3;

    /** Reads "operation, target, user, secret" from stdin; exit 0 ok, 3 not found, anything else failed. Prints only the secret (read). */
    private static final String SCRIPT = String.join("\n",
            "$ErrorActionPreference = 'Stop'",
            "[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)",
            "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)",
            "Add-Type -TypeDefinition @'",
            "using System; using System.Runtime.InteropServices; using System.Text;",
            "public static class SvCred {",
            " [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]",
            " public struct CRED { public UInt32 Flags; public UInt32 Type; public string TargetName; public string Comment;",
            "  public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten; public UInt32 CredentialBlobSize;",
            "  public IntPtr CredentialBlob; public UInt32 Persist; public UInt32 AttributeCount; public IntPtr Attributes;",
            "  public string TargetAlias; public string UserName; }",
            " [DllImport(\"advapi32.dll\", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool CredWriteW(ref CRED c, UInt32 f);",
            " [DllImport(\"advapi32.dll\", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool CredReadW(string t, UInt32 ty, UInt32 f, out IntPtr p);",
            " [DllImport(\"advapi32.dll\", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool CredDeleteW(string t, UInt32 ty, UInt32 f);",
            " [DllImport(\"advapi32.dll\")] static extern void CredFree(IntPtr p);",
            " public static int Write(string target, string user, string secret) {",
            "  byte[] b = Encoding.Unicode.GetBytes(secret); IntPtr m = Marshal.AllocHGlobal(b.Length);",
            "  try { Marshal.Copy(b, 0, m, b.Length); CRED c = new CRED(); c.Type = 1; c.TargetName = target; c.UserName = user;",
            "   c.CredentialBlobSize = (UInt32)b.Length; c.CredentialBlob = m; c.Persist = 2;",
            "   return CredWriteW(ref c, 0) ? 0 : Marshal.GetLastWin32Error(); }",
            "  finally { Marshal.Copy(new byte[b.Length], 0, m, b.Length); Marshal.FreeHGlobal(m); } }",
            " public static int Read(string target, out string secret) {",
            "  secret = null; IntPtr p;",
            "  if (!CredReadW(target, 1, 0, out p)) return Marshal.GetLastWin32Error();",
            "  try { CRED c = (CRED)Marshal.PtrToStructure(p, typeof(CRED));",
            "   secret = Marshal.PtrToStringUni(c.CredentialBlob, (int)c.CredentialBlobSize / 2); return 0; }",
            "  finally { CredFree(p); } }",
            " public static int Delete(string target) { return CredDeleteW(target, 1, 0) ? 0 : Marshal.GetLastWin32Error(); }",
            "}",
            "'@",
            "$op = [Console]::In.ReadLine(); $target = [Console]::In.ReadLine(); $user = [Console]::In.ReadLine(); $secret = [Console]::In.ReadLine()",
            "$rc = 2",
            "if ($op -eq 'write') { $rc = [SvCred]::Write($target, $user, $secret) }",
            "elseif ($op -eq 'read') { $s = $null; $rc = [SvCred]::Read($target, [ref]$s); if ($rc -eq 0) { [Console]::Out.Write($s) } }",
            "elseif ($op -eq 'delete') { $rc = [SvCred]::Delete($target) }",
            "if ($rc -eq 0) { exit 0 } elseif ($rc -eq 1168) { exit 3 } else { [Console]::Error.WriteLine('Win32 error ' + $rc); exit 2 }");

    private static final String ENCODED_SCRIPT = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));

    /** True where the Windows Credential Manager can exist at all. */
    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    @Override
    public void write(String target, String userName, String secret) throws CredentialStoreException {
        if (secret == null || secret.isEmpty() || secret.indexOf('\n') >= 0 || secret.indexOf('\r') >= 0) {
            throw new CredentialStoreException(Failure.FAILED, "The password is empty or contains a line break");
        }
        Result r = run("write", target, userName == null ? "" : userName, secret);
        if (r.exit != EXIT_OK) {
            throw new CredentialStoreException(Failure.FAILED, "Windows Credential Manager refused to save the password");
        }
    }

    @Override
    public Optional<String> read(String target) throws CredentialStoreException {
        Result r = run("read", target, "", "");
        if (r.exit == EXIT_NOT_FOUND) {
            return Optional.empty();
        }
        if (r.exit != EXIT_OK) {
            throw new CredentialStoreException(Failure.FAILED, "Windows Credential Manager could not be read");
        }
        return Optional.of(r.out);
    }

    @Override
    public void delete(String target) throws CredentialStoreException {
        Result r = run("delete", target, "", "");
        if (r.exit != EXIT_OK && r.exit != EXIT_NOT_FOUND) {
            throw new CredentialStoreException(Failure.FAILED, "Windows Credential Manager could not remove the password");
        }
    }

    private record Result(int exit, String out) {
    }

    private static Result run(String op, String target, String user, String secret) throws CredentialStoreException {
        if (!supported()) {
            throw new CredentialStoreException(Failure.UNAVAILABLE, "Windows Credential Manager is only available on Windows");
        }
        Process p = null;
        try {
            p = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", ENCODED_SCRIPT)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (OutputStream in = p.getOutputStream()) {
                in.write((op + "\n" + target + "\n" + user + "\n" + secret + "\n").getBytes(StandardCharsets.UTF_8));
            }
            // the answer is a few bytes, so it fits the pipe buffer: wait first, so a hung process cannot block the read
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new CredentialStoreException(Failure.UNAVAILABLE, "Windows Credential Manager did not answer in time");
            }
            return new Result(p.exitValue(), new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CredentialStoreException(Failure.UNAVAILABLE, "Windows Credential Manager could not be reached (is PowerShell available?)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CredentialStoreException(Failure.UNAVAILABLE, "Interrupted while using Windows Credential Manager");
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }
}
