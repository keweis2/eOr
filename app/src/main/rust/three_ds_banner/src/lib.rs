//! On-device, offscreen 3DS banner rendering. Uses the same materials/camera as the preview.
use std::{error::Error, time::Duration};
use jni::{JNIEnv, objects::{JByteArray, JObject}, sys::jbyteArray};
use cgfx_renderer::Scene;
use cgfx_wgpu::{HomeMenuCamera, PicaLightEnvironment, Renderer};

const WIDTH: u32 = 1200;
const HEIGHT: u32 = 720;
const DEPTH: wgpu::TextureFormat = wgpu::TextureFormat::Depth24PlusStencil8;
const COLOR: wgpu::TextureFormat = wgpu::TextureFormat::Rgba8UnormSrgb;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_gamelaunch_frontend_image_ThreeDsBannerRenderer_nativeRender(
    mut env: JNIEnv, _receiver: JObject, input: JByteArray,
) -> jbyteArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<Vec<u8>, String> {
        let data = env.convert_byte_array(input).map_err(|e| e.to_string())?;
        if data.len() > 0x80000 { return Err("Banner exceeds the CGFX size limit".into()); }
        pollster::block_on(render(&data)).map_err(|e| e.to_string())
    }));
    match result {
        Ok(Ok(bytes)) => env.byte_array_from_slice(&bytes).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut()),
        error => {
            let message = match error { Ok(Err(e)) => e, _ => "Banner renderer failed".into() };
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            std::ptr::null_mut()
        }
    }
}

async fn render(bytes: &[u8]) -> Result<Vec<u8>, Box<dyn Error>> {
    let scene = match cgfx_parser::parse(bytes)? {
        cgfx_parser::GraphicsFile::Cgfx(file) => Scene::from_cgfx(&file)?,
        cgfx_parser::GraphicsFile::Bch(file) => Scene::from_bch(&file)?,
    };
    let frame = 0.0;
    // Offscreen Vulkan needs no Activity, Surface or UI-thread graphics context.
    let mut descriptor = wgpu::InstanceDescriptor::new_without_display_handle();
    descriptor.backends = wgpu::Backends::VULKAN;
    let instance = wgpu::Instance::new(descriptor);
    let adapter = instance.request_adapter(&wgpu::RequestAdapterOptions::default()).await?;
    let (device, queue) = adapter.request_device(&wgpu::DeviceDescriptor::default()).await?;
    let renderer = Renderer::new(&device, COLOR, Some(DEPTH));
    let mut gpu_scene = renderer.upload_scene(&device, &queue, &scene)?;
    let camera = HomeMenuCamera::default();
    renderer.use_home_menu_camera(&queue, camera);
    let mut lighting = PicaLightEnvironment::default();
    // PICA directional vectors describe the incoming rays, so negative Z lights the front.
    lighting.lights[0].position_or_direction = [-0.2, -0.5, -1.0];
    renderer.update_light_environment(&queue, &lighting);
    let mut pose = scene.evaluate_composed(0, frame).unwrap_or_else(|| scene.rest_evaluation(frame));
    pose.apply_home_menu_presentation(&scene, 0.0, camera.view_matrix().inverse());
    renderer.apply_evaluation(&device, &queue, &scene, &mut gpu_scene, &pose);
    let extent = wgpu::Extent3d { width: WIDTH, height: HEIGHT, depth_or_array_layers: 1 };
    let target = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("banner preview"), size: extent, mip_level_count: 1, sample_count: 1,
        dimension: wgpu::TextureDimension::D2, format: COLOR,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
        view_formats: &[],
    });
    let depth = device.create_texture(&wgpu::TextureDescriptor {
        label: Some("banner depth/stencil"), size: extent, mip_level_count: 1, sample_count: 1,
        dimension: wgpu::TextureDimension::D2, format: DEPTH,
        usage: wgpu::TextureUsages::RENDER_ATTACHMENT, view_formats: &[],
    });
    let stride = (WIDTH * 4).div_ceil(wgpu::COPY_BYTES_PER_ROW_ALIGNMENT) * wgpu::COPY_BYTES_PER_ROW_ALIGNMENT;
    let readback = device.create_buffer(&wgpu::BufferDescriptor {
        label: Some("banner PNG readback"), size: u64::from(stride * HEIGHT),
        usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ, mapped_at_creation: false,
    });
    let view = target.create_view(&Default::default());
    let depth_view = depth.create_view(&Default::default());
    let mut encoder = device.create_command_encoder(&Default::default());
    {
        let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
            label: Some("banner scene"),
            color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                view: &view, resolve_target: None, depth_slice: None,
                ops: wgpu::Operations {
                    load: wgpu::LoadOp::Clear(wgpu::Color { r: 0.85, g: 0.90, b: 0.98, a: 1.0 }),
                    store: wgpu::StoreOp::Store,
                },
            })],
            depth_stencil_attachment: Some(wgpu::RenderPassDepthStencilAttachment {
                view: &depth_view,
                depth_ops: Some(wgpu::Operations { load: wgpu::LoadOp::Clear(1.0), store: wgpu::StoreOp::Store }),
                stencil_ops: Some(wgpu::Operations { load: wgpu::LoadOp::Clear(0), store: wgpu::StoreOp::Store }),
            }),
            timestamp_writes: None, occlusion_query_set: None, multiview_mask: None,
        });
        renderer.render(&gpu_scene, &mut pass);
    }
    encoder.copy_texture_to_buffer(
        wgpu::TexelCopyTextureInfo { texture: &target, mip_level: 0, origin: wgpu::Origin3d::ZERO, aspect: wgpu::TextureAspect::All },
        wgpu::TexelCopyBufferInfo { buffer: &readback, layout: wgpu::TexelCopyBufferLayout {
            offset: 0, bytes_per_row: Some(stride), rows_per_image: Some(HEIGHT),
        } }, extent,
    );
    queue.submit([encoder.finish()]);
    let (tx, rx) = std::sync::mpsc::channel();
    readback.slice(..).map_async(wgpu::MapMode::Read, move |result| { let _ = tx.send(result); });
    device.poll(wgpu::PollType::Wait { submission_index: None, timeout: Some(Duration::from_secs(20)) })?;
    rx.recv_timeout(Duration::from_secs(20))??;
    let mapped = readback.slice(..).get_mapped_range();
    let mut rgba = Vec::with_capacity((WIDTH * HEIGHT * 4) as usize);
    for row in mapped.chunks_exact(stride as usize) { rgba.extend_from_slice(&row[..(WIDTH * 4) as usize]); }
    let mut encoded = Vec::new();
    let mut png = png::Encoder::new(&mut encoded, WIDTH, HEIGHT);
    png.set_color(png::ColorType::Rgba);
    png.set_depth(png::BitDepth::Eight);
    png.write_header()?.write_image_data(&rgba)?;
    drop(mapped);
    readback.unmap();
    Ok(encoded)
}
