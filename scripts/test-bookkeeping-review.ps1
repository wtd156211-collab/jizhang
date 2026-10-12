param()

$ErrorActionPreference = 'Stop'
$reviewRoot = Split-Path -Parent $PSScriptRoot
$reviewContainer = 'scaffold-review-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$reviewPreviousUrl = $env:BOOKKEEPING_TEST_JDBC_URL
$reviewCreated = $false

function Assert-ReviewCommand([string]$description) {
    if ($LASTEXITCODE -ne 0) { throw "$description failed (exit $LASTEXITCODE)" }
}

try {
    docker run --detach --rm --name $reviewContainer --label scaffold.test=bookkeeping-review `
        -p '127.0.0.1::3306' -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=bookkeeping_review_test mysql:8.4
    Assert-ReviewCommand 'Create isolated MySQL container'
    $reviewCreated = $true
    $reviewReady = $false
    for ($reviewAttempt = 0; $reviewAttempt -lt 45; $reviewAttempt++) {
        # 初始化阶段也会启动临时 mysqld；要求目标库和 TCP 服务均就绪，避免过早播种。
        docker exec $reviewContainer mysql '--protocol=TCP' '--host=127.0.0.1' '--user=root' '--database=bookkeeping_review_test' -N -e 'SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $reviewReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $reviewReady) { throw 'Isolated MySQL did not become ready' }

    # 先确认服务器、目标库与表结构；仅写入本脚本新建容器中的独立测试库。
    docker exec $reviewContainer mysql -uroot bookkeeping_review_test --batch -e `
        'SELECT DATABASE(), @@hostname, VERSION(); SELECT COUNT(*) AS initial_tables FROM information_schema.TABLES WHERE TABLE_SCHEMA="bookkeeping_review_test";'
    Assert-ReviewCommand 'Inspect isolated database'
    foreach ($reviewSchema in @('V1__init_schema.sql', 'V3__bookkeeping_schema.sql')) {
        $reviewSource = Join-Path $reviewRoot "scaffold-system/sql/$reviewSchema"
        docker cp $reviewSource "${reviewContainer}:/tmp/$reviewSchema"
        Assert-ReviewCommand 'Copy schema'
        docker exec $reviewContainer mysql -uroot bookkeeping_review_test --default-character-set=utf8mb4 -e "source /tmp/$reviewSchema"
        Assert-ReviewCommand 'Initialize schema'
    }
    docker exec $reviewContainer mysql -uroot bookkeeping_review_test --batch -e `
        'SHOW COLUMNS FROM acct_record; SHOW COLUMNS FROM acct_record_review; SHOW COLUMNS FROM acct_record_version; SHOW COLUMNS FROM acct_ocr_task; SHOW COLUMNS FROM sys_config; SHOW COLUMNS FROM sys_user; SHOW COLUMNS FROM sys_menu; SHOW INDEX FROM acct_record_version;'
    Assert-ReviewCommand 'Inspect table structure before inserting fixtures'
    foreach ($reviewSeed in @('V2__seed_data.sql', 'V4__bookkeeping_seed.sql')) {
        $reviewSource = Join-Path $reviewRoot "scaffold-system/sql/$reviewSeed"
        docker cp $reviewSource "${reviewContainer}:/tmp/$reviewSeed"
        Assert-ReviewCommand 'Copy fixture'
        docker exec $reviewContainer mysql -uroot bookkeeping_review_test --default-character-set=utf8mb4 -e "source /tmp/$reviewSeed"
        Assert-ReviewCommand 'Initialize fixtures'
    }
    $reviewMapping = docker port $reviewContainer 3306
    Assert-ReviewCommand 'Read isolated MySQL port'
    if ($reviewMapping -notmatch '^127\.0\.0\.1:([0-9]+)$') { throw 'Unexpected container port mapping' }
    $reviewPort = $Matches[1]
    $env:BOOKKEEPING_TEST_JDBC_URL = "jdbc:mysql://127.0.0.1:$reviewPort/bookkeeping_review_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    Push-Location (Join-Path $reviewRoot 'scaffold-system')
    try {
        mvn -B -ntp -pl scaffold-bookkeeping -am test
        Assert-ReviewCommand 'Bookkeeping unit and MySQL integration tests'
    } finally { Pop-Location }
} finally {
    $env:BOOKKEEPING_TEST_JDBC_URL = $reviewPreviousUrl
    if ($reviewCreated) {
        # --rm 自动清理本脚本创建的容器和匿名卷，不涉及现有容器或开发数据库。
        docker stop $reviewContainer | Out-Null
    }
}
