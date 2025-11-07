const express = require('express');
const sql = require('mssql');
const app = express();

app.use(express.json());

// Cấu hình kết nối SQL Server
const config = {
  user: 'sa',
  password: '123456',
  server: '192.168.1.77', // địa chỉ IP của máy cài SQL Server
  database: 'QuanLyNhaHang',
  options: {
    encrypt: false,
    trustServerCertificate: true
  }
};

// API: Lấy danh sách sản phẩm
app.get('/products', async (req, res) => {
  try {
    await sql.connect(config);
    const result = await sql.query`SELECT * FROM MonAn`;
    res.json(result.recordset);
  } catch (err) {
    res.status(500).send(err.message);
  }
});
app.get('/api/test', async (req, res) => {
    try {
        await sql.connect(config);
        const result = await sql.query`SELECT * FROM Nguoidung`;
        res.json(result.recordset);
    } catch (err) {
        console.error(err);
        res.status(500).send('Lỗi truy vấn SQL: ' + err.message);
    }
});

app.listen(3000, () => console.log('Server running on port 3000'));
