<?php

// savid_Class.php 快递公司划分

require_once __DIR__ . '/savid_Class.php';

// ===================== 统一配置区（只改这里） =====================
//用法提交地址填入 http://你的域名/Exp.php?key=PassWord&id=
//用法查询地址填入 http://你的域名/Exp.php?key=PassWord&cxid=
// 访问密钥：写入 ?key=xxx&id=单号，查询 ?key=xxx&cxid=单号 ，单号软件内自动填写
const MONITOR_VERIFICATION_KEY = 'PassWord';

// 数据库文件路径（写入与查询共用）
const MONITOR_DB_FILE_PATH = __DIR__ . 'monitor.db';
// =================================================================

class LogisticsService {
    private const LOCAL_VERIFICATION_CODE = MONITOR_VERIFICATION_KEY;
    private const DB_FILE_PATH = MONITOR_DB_FILE_PATH;
    private $db;
    private $kdId;
    private $companyName;
    private $message;
    private $messageClass;

    // 构造函数 - 校验验证码与单号，空单号直接返回错误提示，不打开数据库
    public function __construct(string $receivedVerificationCode, string $kdId) {
        $this->validateRequest($receivedVerificationCode);
        $this->kdId = trim($kdId);
        if ($this->kdId === '') {
            $this->message = '单号不能为空';
            $this->messageClass = 'error';
            return;
        }
        try {
            $this->initializeDatabase();
            $this->processLogistics();
        } catch (Throwable $e) {
            $this->message = '写入失败';
            $this->messageClass = 'error';
        }
    }

    private function validateRequest(string $receivedVerificationCode): void {
        if ($receivedVerificationCode !== self::LOCAL_VERIFICATION_CODE) {
            exit('BAD REQUEST');
        }
    }

    // 初始化数据库 - 打开数据库文件并确保日志表存在，失败时抛出异常由构造函数统一处理
    private function initializeDatabase(): void {
        $this->db = new SQLite3(self::DB_FILE_PATH);
        $this->db->enableExceptions(true);
        $this->db->exec("CREATE TABLE IF NOT EXISTS logs (
            id TEXT PRIMARY KEY, 
            timestamp TEXT, 
            company_name TEXT
        )");
    }

    // 处理物流单号 - 识别快递公司并原子写入，依据写入结果判断是否重复
    private function processLogistics(): void {
        $identifier = new LogisticsIdentifier($this->kdId);
        $this->companyName = $identifier->identify();

        if ($this->saveToDatabase()) {
            $this->message = $this->companyName ?: "成功-名称未知";
            $this->messageClass = "success";
        } else {
            $this->message = "✖重复✖" . $this->companyName;
            $this->messageClass = "error";
        }
    }

    // 原子写入日志 - 使用 INSERT OR IGNORE 避免并发主键冲突，返回是否真正新增
    private function saveToDatabase(): bool {
        $stmt = $this->db->prepare("INSERT OR IGNORE INTO logs (id, timestamp, company_name) 
                                   VALUES (:id, :timestamp, :company_name)");
        $stmt->bindValue(':id', $this->kdId, SQLITE3_TEXT);
        $stmt->bindValue(':timestamp', date("Y-m-d H:i:s"), SQLITE3_TEXT);
        $stmt->bindValue(':company_name', $this->companyName, SQLITE3_TEXT);
        $stmt->execute();
        return $this->db->changes() > 0;
    }

    public function getMessage(): string {
        return $this->message;
    }

    public function getMessageClass(): string {
        return $this->messageClass;
    }

    public function getKdId(): string {
        return $this->kdId;
    }
}

/**
 * 模糊匹配，包含传入字符串；按最新时间倒序，最多返回前10条
 */
function getKDIdLikeRecord(string $keyword, SQLite3 $db): array {
    $stmt = $db->prepare("SELECT id, timestamp FROM logs WHERE id LIKE :kw 
                          ORDER BY timestamp DESC, rowid DESC LIMIT 10");
    $likeVal = '%' . $keyword . '%';
    $stmt->bindValue(':kw', $likeVal, SQLITE3_TEXT);
    $res = $stmt->execute();
    $list = [];
    while ($row = $res->fetchArray(SQLITE3_ASSOC)) {
        $list[] = $row;
    }
    return $list;
}

// 查询功能 - 调用格式 key=MONITOR_VERIFICATION_KEY的值&cxid=，返回 JSON
function handleQuery(string $receivedVerificationCode, string $KDId): never {
    if ($receivedVerificationCode !== MONITOR_VERIFICATION_KEY) {
        exit('BAD REQUEST');
    }

    header('Content-Type: application/json; charset=utf-8');

    $resp = [
        'code' => 0,
        'msg'  => '',
        'data' => []
    ];

    $KDId = trim($KDId);

    if ($KDId === '') {
        $resp['code'] = 400;
        $resp['msg']  = '网络查询不能为空：';
        echo json_encode($resp, JSON_UNESCAPED_UNICODE);
        exit;
    }

    try {
        $db = new SQLite3(MONITOR_DB_FILE_PATH, SQLITE3_OPEN_READONLY);
        $db->enableExceptions(true);
        $records = getKDIdLikeRecord($KDId, $db);
        $db->close();

        if (!empty($records)) {
            $resp['code'] = 200;
            $resp['msg']  = '最新10条：';
            $resp['data'] = $records;
        } else {
            $resp['code'] = 404;
            $resp['msg']  = '无匹配：';
            $resp['data'] = [];
        }
    } catch (Throwable $e) {
        $resp['code'] = 500;
        $resp['msg']  = '查询失败：' . $e->getMessage();
    }

    echo json_encode($resp, JSON_UNESCAPED_UNICODE);
    exit;
}

// 使用示例 - 先校验请求参数类型，避免数组参数触发类型错误
$Global_RequestKey = $_GET['key'] ?? '';
if (!is_string($Global_RequestKey)) {
    exit('BAD REQUEST');
}

// 查询分支：key=密钥&cxid=xxxx
if (isset($_GET['cxid'])) {
    $Global_CxId = $_GET['cxid'];
    handleQuery($Global_RequestKey, is_string($Global_CxId) ? $Global_CxId : '');
}

// 写入分支：key=密钥&id=xxxx（原有逻辑，不变）
$Global_RequestId = $_GET['id'] ?? '';
if (!is_string($Global_RequestId)) {
    exit('BAD REQUEST');
}
$service = new LogisticsService($Global_RequestKey, $Global_RequestId);
?>

<!DOCTYPE html>
<html lang="zh">
<head>
    <meta charset="UTF-8">
    <title>操作反馈</title>
    <style>
        body { 
            display: flex; 
            justify-content: center; 
            align-items: center; 
            height: 100vh; 
            margin: 0; 
            background-color: #f7f7f7; 
            font-family: Arial, sans-serif; 
        }
        #messageBox { 
            text-align: center; 
            padding: 20px; 
            border-radius: 10px; 
            box-shadow: 0 4px 8px rgba(0,0,0,.1); 
            background-color: white; 
            transition: transform .3s ease-in-out; 
        }
        #messageBox.success { color: #28a745; }
        #messageBox.error { color: #dc3545; }
        @keyframes fadeIn { 
            from { opacity: 0; transform: scale(.9); } 
            to { opacity: 1; transform: scale(1); } 
        }
        #messageBox { animation: fadeIn .5s; }
    </style>
</head>
<body onclick="copyKDId()">
    <div id="messageBox" class="<?= htmlspecialchars($service->getMessageClass()) ?>">
        <p style="font-size: 24px;">操作结果：</p>
        <p style="font-size: 48px;"><?= htmlspecialchars($service->getMessage()) ?></p>
        <p>单号: <span id="kdIdSpan"><?= htmlspecialchars($service->getKdId()) ?></span></p>
    </div>

    <script>
    function copyKDId() {
        const kdIdSpan = document.getElementById('kdIdSpan');
        const tempInput = document.createElement('input');
        tempInput.value = kdIdSpan.innerText;
        document.body.appendChild(tempInput);
        tempInput.select();
        document.execCommand('copy');
        document.body.removeChild(tempInput);
        alert('单号 已复制到剪贴板');
    }
    </script>
</body>
</html>
