class AndroidPerfCli < Formula
  desc "Android performance monitoring CLI (leaks, StrictMode, frames, ANR hints, Perfetto)"
  homepage "https://github.com/santimattius/android-perf-cli"
  url "https://github.com/santimattius/android-perf-cli/releases/download/v0.1.0/perf-cli-0.1.0.jar"
  version "0.1.0"
  sha256 "00cdb3b350364cd469edacc9c525ac55ec74de41f5f6e3eed1810736a3aad587"
  license "Apache-2.0"

  depends_on "openjdk@17"

  def install
    libexec.install "perf-cli-0.1.0.jar" => "perf-cli.jar"
    bin.write_jar_script libexec/"perf-cli.jar", "perf"
  end

  test do
    assert_match "perf", shell_output("#{bin}/perf --help")
  end
end
