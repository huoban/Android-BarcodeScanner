<?php
/**
 * 物流公司识别类
 */
class LogisticsIdentifier 
{
    private $text;

    public function __construct(string $text) 
    {
        $this->text = trim($text);
    }

    /**
     * 识别物流公司
     * @return string 物流公司名称或空字符串
     */
    public function identify(): string
    {
        $length = mb_strlen($this->text, 'UTF-8');
        $prefix = mb_substr($this->text, 0, 2, 'UTF-8');
        $firstChar = mb_substr($this->text, 0, 1, 'UTF-8');

        // 按长度优先的顺序检查
        $lengthBased = [
            15 => [
                '7' => '申通',
                '3' => '韵达',
                '4' => '韵达',
                '6' => '丹鸟'
            ],
            14 => [
                '7' => '中通'
            ],
            13 => [
                '9' => '邮政-电商特惠',
                '1' => '邮政-EMS航空',
                '5' => '邮政-EMS普快'
            ]
        ];

        if (isset($lengthBased[$length]) && isset($lengthBased[$length][$firstChar])) {
            return $lengthBased[$length][$firstChar];
        }

        // 检查前缀
        $prefixBased = [
            'JD' => '京东',
            'JT' => '极兔',
            'SF' => '顺丰',
            'YT' => '圆通',
            'DP' => '德邦'
        ];

        return $prefixBased[$prefix] ?? '';
    }
}